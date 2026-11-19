package dev.taskflow.application;

import dev.taskflow.domain.Priority;
import dev.taskflow.domain.Status;
import dev.taskflow.domain.Task;
import dev.taskflow.domain.TaskNotFoundException;
import dev.taskflow.domain.Urgency;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use cases for managing tasks. Stateless: all state lives behind {@link TaskRepository}, and all
 * time comes from the injected {@link Clock}.
 *
 * <p>Log lines record <em>what happened</em> (ids, counts) but never task titles, which are
 * user-authored and may be sensitive.
 */
public final class TaskService {

  private static final Logger LOG = LoggerFactory.getLogger(TaskService.class);

  /** Open before done, then earliest due date first (undated last), then highest priority. */
  private static final Comparator<Task> DISPLAY_ORDER =
      Comparator.comparing(Task::status)
          .thenComparing(Task::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(Task::priority, Comparator.reverseOrder())
          .thenComparingLong(Task::id);

  /** Outcome of a bulk import. */
  public record ImportResult(int added, int duplicates) {}

  private final TaskRepository repository;
  private final Clock clock;

  public TaskService(TaskRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public LocalDate today() {
    return LocalDate.now(clock);
  }

  public Task add(String title, Priority priority, LocalDate dueDate, Collection<String> tags) {
    Task saved = repository.save(Task.create(title, priority, dueDate, tags, now()));
    LOG.info(
        "task_added id={} priority={} has_due_date={} tag_count={}",
        saved.id(),
        saved.priority(),
        saved.dueDate() != null,
        saved.tags().size());
    return saved;
  }

  public List<Task> list(TaskQuery query) {
    LocalDate today = today();
    return repository.findAll().stream()
        .filter(task -> matchesStatus(task, query.status()))
        .filter(task -> query.tag() == null || task.tags().contains(query.tag()))
        .filter(task -> !query.overdueOnly() || task.urgency(today, 0) == Urgency.OVERDUE)
        .sorted(DISPLAY_ORDER)
        .toList();
  }

  public Task complete(long id) {
    Task task = require(id);
    Task done = task.complete(now());
    if (done == task) {
      LOG.debug("task_complete_noop id={} reason=already_done", id);
      return task;
    }
    Task saved = repository.save(done);
    LOG.info("task_completed id={}", id);
    return saved;
  }

  public Task reopen(long id) {
    Task task = require(id);
    Task reopened = task.reopen();
    if (reopened == task) {
      LOG.debug("task_reopen_noop id={} reason=already_open", id);
      return task;
    }
    Task saved = repository.save(reopened);
    LOG.info("task_reopened id={}", id);
    return saved;
  }

  public void delete(long id) {
    if (!repository.deleteById(id)) {
      throw new TaskNotFoundException(id);
    }
    LOG.info("task_deleted id={}", id);
  }

  /**
   * Imports tasks idempotently: a candidate with the same title and due date as an existing task
   * (or an earlier candidate) is skipped, so re-running an import never creates duplicates.
   */
  public ImportResult importTasks(List<Task> candidates) {
    Set<String> seen = new HashSet<>();
    for (Task existing : repository.findAll()) {
      seen.add(identityKey(existing));
    }
    List<Task> toAdd = new ArrayList<>();
    for (Task candidate : candidates) {
      if (seen.add(identityKey(candidate))) {
        toAdd.add(candidate);
      }
    }
    if (!toAdd.isEmpty()) {
      repository.saveAll(toAdd);
    }
    ImportResult result = new ImportResult(toAdd.size(), candidates.size() - toAdd.size());
    LOG.info("tasks_imported added={} duplicates={}", result.added(), result.duplicates());
    return result;
  }

  private Task require(long id) {
    return repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.SECONDS);
  }

  private static boolean matchesStatus(Task task, TaskQuery.StatusFilter filter) {
    return switch (filter) {
      case ALL -> true;
      case OPEN -> task.status() == Status.OPEN;
      case DONE -> task.status() == Status.DONE;
    };
  }

  private static String identityKey(Task task) {
    return task.title().toLowerCase(java.util.Locale.ROOT) + '\u0000' + task.dueDate();
  }
}
