package dev.taskflow.application;

import dev.taskflow.domain.Task;
import dev.taskflow.domain.TaskNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Test double for {@link TaskRepository}. Because services depend on the port, business logic is
 * tested here without touching the file system; the real adapter is held to the same behaviour by
 * {@link TaskRepositoryContractTest}.
 */
public final class InMemoryTaskRepository implements TaskRepository {

  private final List<Task> tasks = new ArrayList<>();
  private long nextId = 1;

  @Override
  public synchronized List<Task> findAll() {
    return List.copyOf(tasks);
  }

  @Override
  public synchronized Optional<Task> findById(long id) {
    return tasks.stream().filter(task -> task.id() == id).findFirst();
  }

  @Override
  public synchronized Task save(Task task) {
    if (task.id() == 0) {
      Task saved = task.withId(nextId++);
      tasks.add(saved);
      return saved;
    }
    for (int i = 0; i < tasks.size(); i++) {
      if (tasks.get(i).id() == task.id()) {
        tasks.set(i, task);
        return task;
      }
    }
    throw new TaskNotFoundException(task.id());
  }

  @Override
  public synchronized List<Task> saveAll(List<Task> newTasks) {
    List<Task> saved = new ArrayList<>();
    for (Task task : newTasks) {
      saved.add(save(task));
    }
    return List.copyOf(saved);
  }

  @Override
  public synchronized boolean deleteById(long id) {
    return tasks.removeIf(task -> task.id() == id);
  }
}
