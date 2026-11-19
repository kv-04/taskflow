package dev.taskflow.application;

import dev.taskflow.domain.Task;
import dev.taskflow.domain.TaskNotFoundException;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port. The application layer depends on this interface only; storage technology is an
 * infrastructure detail (see ADR-0002) and can be swapped without touching business logic.
 *
 * <p>Implementations must be safe for concurrent use, including by multiple processes.
 */
public interface TaskRepository {

  List<Task> findAll();

  Optional<Task> findById(long id);

  /**
   * Inserts the task if its id is {@code 0} (assigning a fresh, never-reused id), otherwise updates
   * the existing task.
   *
   * @throws TaskNotFoundException when updating a task that does not exist
   */
  Task save(Task task);

  /** Inserts several new tasks in one atomic write. Ids are assigned in list order. */
  List<Task> saveAll(List<Task> newTasks);

  /** Returns {@code true} if a task was deleted. */
  boolean deleteById(long id);
}
