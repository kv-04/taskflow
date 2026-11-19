package dev.taskflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.taskflow.domain.Priority;
import dev.taskflow.domain.Task;
import dev.taskflow.domain.TaskNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Behavioural contract every {@link TaskRepository} must satisfy. Concrete adapters extend this
 * class, so the in-memory double and the real file-backed implementation cannot drift apart.
 */
public abstract class TaskRepositoryContractTest {

  private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

  protected abstract TaskRepository newRepository();

  private static Task draft(String title) {
    return Task.create(title, Priority.MEDIUM, null, List.of(), NOW);
  }

  @Test
  public void startsEmpty() {
    assertThat(newRepository().findAll()).isEmpty();
    assertThat(newRepository().findById(1)).isEmpty();
  }

  @Test
  public void assignsIncreasingIdsStartingAtOne() {
    TaskRepository repository = newRepository();
    assertThat(repository.save(draft("a")).id()).isEqualTo(1);
    assertThat(repository.save(draft("b")).id()).isEqualTo(2);
  }

  @Test
  public void roundTripsEveryField() {
    TaskRepository repository = newRepository();
    Task original =
        Task.create("Ship it", Priority.HIGH, LocalDate.of(2026, 12, 1), List.of("work", "q4"), NOW)
            .complete(NOW.plusSeconds(5));

    Task saved = repository.save(original);

    assertThat(repository.findById(saved.id())).contains(saved);
    assertThat(saved).isEqualTo(original.withId(saved.id()));
  }

  @Test
  public void updateReplacesTheExistingTask() {
    TaskRepository repository = newRepository();
    Task saved = repository.save(draft("a"));

    repository.save(saved.complete(NOW));

    assertThat(repository.findAll()).hasSize(1);
    assertThat(repository.findById(saved.id()).orElseThrow().isDone()).isTrue();
  }

  @Test
  public void updatingAnUnknownTaskFails() {
    Task ghost = draft("ghost").withId(42);
    TaskRepository repository = newRepository();
    assertThatThrownBy(() -> repository.save(ghost)).isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  public void deleteReportsWhetherSomethingWasRemoved() {
    TaskRepository repository = newRepository();
    Task saved = repository.save(draft("a"));

    assertThat(repository.deleteById(saved.id())).isTrue();
    assertThat(repository.deleteById(saved.id())).isFalse();
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  public void idsAreNeverReusedAfterDeletion() {
    TaskRepository repository = newRepository();
    repository.save(draft("a"));
    Task second = repository.save(draft("b"));
    repository.deleteById(second.id());

    assertThat(repository.save(draft("c")).id()).isEqualTo(3);
  }

  @Test
  public void saveAllAssignsIdsInOrder() {
    TaskRepository repository = newRepository();

    List<Task> saved = repository.saveAll(List.of(draft("a"), draft("b"), draft("c")));

    assertThat(saved).extracting(Task::id).containsExactly(1L, 2L, 3L);
    assertThat(saved).extracting(Task::title).containsExactly("a", "b", "c");
    assertThat(repository.findAll()).hasSize(3);
  }
}
