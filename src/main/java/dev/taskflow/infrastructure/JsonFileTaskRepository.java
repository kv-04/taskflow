package dev.taskflow.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.taskflow.application.TaskRepository;
import dev.taskflow.domain.StorageException;
import dev.taskflow.domain.Task;
import dev.taskflow.domain.TaskNotFoundException;
import dev.taskflow.domain.ValidationException;
import dev.taskflow.infrastructure.StorageDocument.StoredTask;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores tasks in a single versioned JSON file.
 *
 * <ul>
 *   <li><b>Crash-safe:</b> every write goes to a temp file in the same directory, is fsync'd, then
 *       atomically renamed over the target. A crash or power loss leaves either the old or the new
 *       file, never a truncated one.
 *   <li><b>Concurrency-safe:</b> each operation is a read-modify-write transaction under an
 *       exclusive OS file lock (across processes) plus a JVM-wide lock (an OS lock cannot be taken
 *       twice by one JVM).
 *   <li><b>Private by default:</b> on POSIX systems the directory is {@code 0700} and files {@code
 *       0600}.
 *   <li><b>Fail loudly:</b> unreadable, corrupt or newer-schema files raise {@link
 *       StorageException} and are never overwritten.
 *   <li><b>Bounded:</b> files over {@value #MAX_FILE_BYTES} bytes are refused.
 * </ul>
 */
public final class JsonFileTaskRepository implements TaskRepository {

  static final int SCHEMA_VERSION = 1;
  static final long MAX_FILE_BYTES = 16L * 1024 * 1024;

  private static final Logger LOG = LoggerFactory.getLogger(JsonFileTaskRepository.class);
  private static final ConcurrentMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();
  private static final boolean POSIX =
      java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

  private static final ObjectMapper MAPPER = createMapper();

  private final Path file;
  private final Path lockFile;
  private final ReentrantLock jvmLock;

  public JsonFileTaskRepository(Path file) {
    this.file = file.toAbsolutePath().normalize();
    this.lockFile = this.file.resolveSibling(this.file.getFileName() + ".lock");
    this.jvmLock = JVM_LOCKS.computeIfAbsent(this.file, ignored -> new ReentrantLock());
    createPrivateDirectory(this.file.getParent());
  }

  @Override
  public List<Task> findAll() {
    return transaction(state -> List.copyOf(state.tasks));
  }

  @Override
  public Optional<Task> findById(long id) {
    return transaction(
        state -> state.tasks.stream().filter(task -> task.id() == id).findFirst());
  }

  @Override
  public Task save(Task task) {
    return transaction(
        state -> {
          if (task.id() == 0) {
            return insert(state, task);
          }
          int index = indexOf(state, task.id());
          if (index < 0) {
            throw new TaskNotFoundException(task.id());
          }
          state.tasks.set(index, task);
          state.dirty = true;
          return task;
        });
  }

  @Override
  public List<Task> saveAll(List<Task> newTasks) {
    return transaction(
        state -> {
          List<Task> saved = new ArrayList<>(newTasks.size());
          for (Task task : newTasks) {
            saved.add(insert(state, task));
          }
          return List.copyOf(saved);
        });
  }

  @Override
  public boolean deleteById(long id) {
    return transaction(
        state -> {
          int index = indexOf(state, id);
          if (index < 0) {
            return false;
          }
          state.tasks.remove(index);
          state.dirty = true;
          return true;
        });
  }

  // ---------------------------------------------------------------------------------------------

  private static Task insert(State state, Task task) {
    // Ids are never reused, even after deletion, so a stale id can't silently point at a new task.
    Task saved = task.withId(state.nextId++);
    state.tasks.add(saved);
    state.dirty = true;
    return saved;
  }

  private static int indexOf(State state, long id) {
    for (int i = 0; i < state.tasks.size(); i++) {
      if (state.tasks.get(i).id() == id) {
        return i;
      }
    }
    return -1;
  }

  private <T> T transaction(Function<State, T> work) {
    jvmLock.lock();
    try (FileChannel channel =
            FileChannel.open(
                lockFile,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE),
                privateFileAttributes());
        FileLock lock = channel.lock()) {
      LOG.trace("lock_acquired valid={}", lock.isValid());
      State state = read();
      T result = work.apply(state);
      if (state.dirty) {
        write(state);
      }
      return result;
    } catch (IOException e) {
      throw new StorageException("Cannot access task storage at " + file + ": " + e.getMessage(), e);
    } finally {
      jvmLock.unlock();
    }
  }

  private State read() throws IOException {
    if (!Files.exists(file)) {
      return new State(new ArrayList<>(), 1);
    }
    long size = Files.size(file);
    if (size > MAX_FILE_BYTES) {
      throw new StorageException(
          "Task file " + file + " is " + size + " bytes, over the " + MAX_FILE_BYTES + " byte limit");
    }
    StorageDocument document;
    try {
      document = MAPPER.readValue(Files.readAllBytes(file), StorageDocument.class);
    } catch (IOException e) {
      throw new StorageException(
          "Task file " + file + " is corrupt and was left untouched: " + firstLine(e.getMessage()), e);
    }
    if (document == null || document.tasks() == null) {
      throw new StorageException("Task file " + file + " is empty or malformed and was left untouched");
    }
    if (document.schemaVersion() != SCHEMA_VERSION) {
      throw new StorageException(
          "Task file "
              + file
              + " uses schema version "
              + document.schemaVersion()
              + " but this build supports "
              + SCHEMA_VERSION
              + "; upgrade taskflow");
    }
    List<Task> tasks = new ArrayList<>(document.tasks().size());
    long maxId = 0;
    try {
      for (StoredTask stored : document.tasks()) {
        Task task = fromStored(stored);
        tasks.add(task);
        maxId = Math.max(maxId, task.id());
      }
    } catch (ValidationException e) {
      throw new StorageException(
          "Task file " + file + " contains an invalid task and was left untouched: " + e.getMessage(), e);
    }
    return new State(tasks, Math.max(document.nextId(), maxId + 1));
  }

  private void write(State state) throws IOException {
    List<StoredTask> stored = state.tasks.stream().map(JsonFileTaskRepository::toStored).toList();
    byte[] bytes = MAPPER.writeValueAsBytes(new StorageDocument(SCHEMA_VERSION, state.nextId, stored));

    Path temp = Files.createTempFile(file.getParent(), "tasks-", ".tmp"); // 0600 on POSIX
    try {
      try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true); // fsync before the rename makes the new content visible
      }
      Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp); // no-op after a successful move
    }
    LOG.debug("storage_written bytes={} tasks={}", bytes.length, state.tasks.size());
  }

  private static StoredTask toStored(Task task) {
    return new StoredTask(
        task.id(),
        task.title(),
        task.status(),
        task.priority(),
        task.dueDate(),
        List.copyOf(task.tags()),
        task.createdAt(),
        task.completedAt());
  }

  private static Task fromStored(StoredTask stored) {
    return new Task(
        stored.id(),
        stored.title(),
        stored.status(),
        stored.priority(),
        stored.dueDate(),
        stored.tags() == null ? Set.of() : new HashSet<>(stored.tags()),
        stored.createdAt(),
        stored.completedAt());
  }

  private static void createPrivateDirectory(Path directory) {
    try {
      if (POSIX) {
        Files.createDirectories(
            directory,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      } else {
        Files.createDirectories(directory);
      }
    } catch (IOException e) {
      throw new StorageException("Cannot create data directory " + directory + ": " + e.getMessage(), e);
    }
  }

  private static FileAttribute<?>[] privateFileAttributes() {
    return POSIX
        ? new FileAttribute<?>[] {
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
        }
        : new FileAttribute<?>[0];
  }

  private static String firstLine(String message) {
    if (message == null) {
      return "unreadable content";
    }
    int newline = message.indexOf('\n');
    return newline < 0 ? message : message.substring(0, newline);
  }

  private static ObjectMapper createMapper() {
    JsonFactory factory =
        JsonFactory.builder()
            .streamReadConstraints(
                StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(10_000).build())
            .build();
    return new ObjectMapper(factory)
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(SerializationFeature.INDENT_OUTPUT) // human-readable and diff-friendly
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  /** Mutable working copy of the file's contents, confined to one locked transaction. */
  private static final class State {
    final List<Task> tasks;
    long nextId;
    boolean dirty;

    State(List<Task> tasks, long nextId) {
      this.tasks = tasks;
      this.nextId = nextId;
    }
  }
}
