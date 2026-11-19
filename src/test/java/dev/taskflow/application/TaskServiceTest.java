package dev.taskflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.taskflow.application.TaskQuery.StatusFilter;
import dev.taskflow.application.TaskService.ImportResult;
import dev.taskflow.domain.Priority;
import dev.taskflow.domain.Task;
import dev.taskflow.domain.TaskNotFoundException;
import dev.taskflow.domain.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-04T12:00:00.789Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private InMemoryTaskRepository repository;
  private TaskService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryTaskRepository();
    service = new TaskService(repository, CLOCK);
  }

  private Task add(String title, String due) {
    return service.add(title, Priority.MEDIUM, due == null ? null : LocalDate.parse(due), null);
  }

  @Test
  void addTruncatesCreationTimeToSeconds() {
    Task task = add("a", null);
    assertThat(task.createdAt()).isEqualTo(Instant.parse("2026-11-04T12:00:00Z"));
  }

  @Test
  void addPropagatesValidationErrors() {
    assertThatThrownBy(() -> add(" ", null)).isInstanceOf(ValidationException.class);
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void listSortsOpenFirstThenByDueDateThenPriorityDescending() {
    Task undated = add("undated", null);
    Task later = add("later", "2026-12-01");
    Task lowSoon = service.add("low soon", Priority.LOW, LocalDate.parse("2026-11-05"), null);
    Task highSoon = service.add("high soon", Priority.HIGH, LocalDate.parse("2026-11-05"), null);
    Task finished = service.complete(add("finished", "2026-01-01").id());

    List<Task> listed = service.list(new TaskQuery(StatusFilter.ALL, null, false));

    assertThat(listed)
        .extracting(Task::id)
        .containsExactly(highSoon.id(), lowSoon.id(), later.id(), undated.id(), finished.id());
  }

  @Test
  void listFiltersByStatusTagAndOverdue() {
    Task overdue = service.add("o", Priority.LOW, LocalDate.parse("2026-11-01"), List.of("home"));
    Task today = service.add("t", Priority.LOW, LocalDate.parse("2026-11-04"), List.of("work"));
    Task done = add("d", null);
    service.complete(done.id());

    assertThat(service.list(TaskQuery.openTasks())).extracting(Task::id)
        .containsExactly(overdue.id(), today.id());
    assertThat(service.list(new TaskQuery(StatusFilter.DONE, null, false))).extracting(Task::id)
        .containsExactly(done.id());
    assertThat(service.list(new TaskQuery(StatusFilter.OPEN, "#HOME", false))).extracting(Task::id)
        .containsExactly(overdue.id());
    assertThat(service.list(new TaskQuery(StatusFilter.OPEN, null, true))).extracting(Task::id)
        .containsExactly(overdue.id());
  }

  @Test
  void queryRejectsInvalidTagsAndDefaultsToOpen() {
    assertThatThrownBy(() -> new TaskQuery(null, "bad tag", false))
        .isInstanceOf(ValidationException.class);
    assertThat(new TaskQuery(null, null, false).status()).isEqualTo(StatusFilter.OPEN);
  }

  @Test
  void completeStampsTimeAndIsIdempotent() {
    Task task = add("a", null);

    Task done = service.complete(task.id());
    Task again = service.complete(task.id());

    assertThat(done.isDone()).isTrue();
    assertThat(done.completedAt()).isEqualTo(Instant.parse("2026-11-04T12:00:00Z"));
    assertThat(again).isEqualTo(done);
  }

  @Test
  void reopenRestoresOpenStateAndIsIdempotent() {
    Task task = add("a", null);
    service.complete(task.id());

    assertThat(service.reopen(task.id()).isDone()).isFalse();
    assertThat(service.reopen(task.id()).completedAt()).isNull();
  }

  @Test
  void operationsOnUnknownIdsFailWithNotFound() {
    assertThatThrownBy(() -> service.complete(7)).isInstanceOf(TaskNotFoundException.class)
        .hasMessage("No task with id 7");
    assertThatThrownBy(() -> service.reopen(7)).isInstanceOf(TaskNotFoundException.class);
    assertThatThrownBy(() -> service.delete(7)).isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void deleteRemovesTheTask() {
    Task task = add("a", null);
    service.delete(task.id());
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void importSkipsDuplicatesAgainstExistingDataAndWithinTheBatch() {
    add("Shopping", "2026-11-09");
    List<Task> candidates =
        List.of(
            Task.create("shopping", Priority.MEDIUM, LocalDate.parse("2026-11-09"), null, NOW), // dup (case-insensitive)
            Task.create("shopping", Priority.MEDIUM, LocalDate.parse("2026-11-10"), null, NOW), // new date
            Task.create("new", Priority.MEDIUM, null, null, NOW),
            Task.create("NEW", Priority.MEDIUM, null, null, NOW)); // dup within batch

    ImportResult result = service.importTasks(candidates);

    assertThat(result).isEqualTo(new ImportResult(2, 2));
    assertThat(repository.findAll()).hasSize(3);
    assertThat(service.importTasks(candidates)).isEqualTo(new ImportResult(0, 4));
  }

  @Test
  void todayComesFromTheInjectedClock() {
    assertThat(service.today()).isEqualTo(LocalDate.of(2026, 11, 4));
  }
}
