package dev.taskflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.taskflow.application.TaskRepository;
import dev.taskflow.application.TaskRepositoryContractTest;
import dev.taskflow.domain.Priority;
import dev.taskflow.domain.StorageException;
import dev.taskflow.domain.Task;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the shared repository contract against the real adapter, plus storage-specific guarantees. */
class JsonFileTaskRepositoryTest extends TaskRepositoryContractTest {

  @TempDir Path dir;

  private Path file() {
    return dir.resolve("data").resolve("tasks.json");
  }

  @Override
  protected TaskRepository newRepository() {
    return new JsonFileTaskRepository(file());
  }

  private static Task draft(String title) {
    return Task.create(title, Priority.LOW, null, List.of(), Instant.parse("2026-11-04T12:00:00Z"));
  }

  @Test
  void dataSurvivesANewRepositoryInstance() {
    newRepository().save(draft("persisted"));

    assertThat(newRepository().findAll()).extracting(Task::title).containsExactly("persisted");
  }

  @Test
  void writesVersionedHumanReadableJson() throws IOException {
    newRepository().save(draft("hello"));

    String json = Files.readString(file());
    assertThat(json).contains("\"schemaVersion\" : 1").contains("\"title\" : \"hello\"");
    assertThat(json).doesNotContain("null"); // absent optional fields are omitted, not nulled
  }

  @Test
  void leavesNoTemporaryFilesBehind() throws IOException {
    TaskRepository repository = newRepository();
    repository.save(draft("a"));
    repository.save(draft("b"));

    try (Stream<Path> files = Files.list(file().getParent())) {
      assertThat(files.map(p -> p.getFileName().toString())).noneMatch(name -> name.endsWith(".tmp"));
    }
  }

  @Test
  void usesOwnerOnlyPermissionsOnPosixSystems() throws IOException {
    assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    newRepository().save(draft("secret"));

    assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file())))
        .isEqualTo("rw-------");
    assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file().getParent())))
        .isEqualTo("rwx------");
  }

  @Test
  void concurrentWritersAcrossInstancesLoseNoUpdatesAndNeverShareIds() throws Exception {
    TaskRepository first = newRepository();
    TaskRepository second = newRepository();
    int threads = 8;
    int perThread = 25;

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        TaskRepository target = t % 2 == 0 ? first : second;
        int worker = t;
        futures.add(
            pool.submit(
                () -> {
                  for (int i = 0; i < perThread; i++) {
                    target.save(draft("w" + worker + "-" + i));
                  }
                }));
      }
      for (Future<?> future : futures) {
        future.get();
      }
    } finally {
      pool.shutdownNow();
    }

    List<Task> all = first.findAll();
    assertThat(all).hasSize(threads * perThread);
    assertThat(all).extracting(Task::id).doesNotHaveDuplicates();
  }

  @Test
  void corruptFileFailsLoudlyAndIsNeverOverwritten() throws IOException {
    TaskRepository repository = newRepository();
    Files.writeString(file(), "{ definitely not json");

    assertThatThrownBy(repository::findAll)
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("corrupt");
    assertThatThrownBy(() -> repository.save(draft("would clobber")))
        .isInstanceOf(StorageException.class);
    assertThat(Files.readString(file())).isEqualTo("{ definitely not json");
  }

  @Test
  void refusesFilesFromANewerSchema() throws IOException {
    TaskRepository repository = newRepository();
    Files.writeString(file(), "{\"schemaVersion\":99,\"nextId\":1,\"tasks\":[]}");

    assertThatThrownBy(repository::findAll)
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("schema version 99");
  }

  @Test
  void rejectsUnknownFieldsSoTyposAndTamperingAreNotSilentlyDropped() throws IOException {
    TaskRepository repository = newRepository();
    Files.writeString(file(), "{\"schemaVersion\":1,\"nextId\":1,\"tasks\":[],\"surprise\":true}");

    assertThatThrownBy(repository::findAll).isInstanceOf(StorageException.class);
  }

  @Test
  void revalidatesDomainRulesOnLoadSoHandEditedFilesCannotInjectEscapeCodes() throws IOException {
    TaskRepository repository = newRepository();
    Files.writeString(
        file(),
        "{\"schemaVersion\":1,\"nextId\":2,\"tasks\":[{\"id\":1,\"title\":\"x\\u001b[2J\","
            + "\"status\":\"OPEN\",\"priority\":\"LOW\",\"createdAt\":\"2026-01-01T00:00:00Z\"}]}");

    assertThatThrownBy(repository::findAll)
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("invalid task");
  }

  @Test
  void refusesAbsurdlyLargeFilesWithoutReadingThem() throws IOException {
    TaskRepository repository = newRepository();
    byte[] big = new byte[(int) (JsonFileTaskRepository.MAX_FILE_BYTES + 1)];
    Files.write(file(), big);

    assertThatThrownBy(repository::findAll)
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("limit");
  }

  @Test
  void repairsNextIdIfTheFileClaimsAnIdThatIsAlreadyInUse() throws IOException {
    TaskRepository repository = newRepository();
    Files.writeString(
        file(),
        "{\"schemaVersion\":1,\"nextId\":1,\"tasks\":[{\"id\":5,\"title\":\"x\","
            + "\"status\":\"OPEN\",\"priority\":\"LOW\",\"createdAt\":\"2026-01-01T00:00:00Z\"}]}");

    assertThat(repository.save(draft("y")).id()).isEqualTo(6);
  }
}
