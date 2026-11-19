package dev.taskflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.taskflow.application.ReminderService.Reminder;
import dev.taskflow.domain.Priority;
import dev.taskflow.domain.Urgency;
import dev.taskflow.domain.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReminderServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);

  private TaskService tasks;
  private ReminderService reminders;

  @BeforeEach
  void setUp() {
    InMemoryTaskRepository repository = new InMemoryTaskRepository();
    tasks = new TaskService(repository, CLOCK);
    reminders = new ReminderService(repository, CLOCK);
  }

  private long add(String title, String due, Priority priority) {
    return tasks.add(title, priority, due == null ? null : LocalDate.parse(due), null).id();
  }

  @Test
  void reportsOverdueTodayAndSoonMostUrgentFirst() {
    long soon = add("soon", "2026-11-05", Priority.LOW);
    long today = add("today", "2026-11-04", Priority.LOW);
    long overdue = add("overdue", "2026-11-01", Priority.LOW);
    add("later", "2026-11-20", Priority.LOW);
    add("undated", null, Priority.LOW);

    List<Reminder> result = reminders.upcoming(1);

    assertThat(result).extracting(r -> r.task().id()).containsExactly(overdue, today, soon);
    assertThat(result).extracting(Reminder::urgency)
        .containsExactly(Urgency.OVERDUE, Urgency.TODAY, Urgency.SOON);
    assertThat(result).extracting(Reminder::daysUntilDue).containsExactly(-3L, 0L, 1L);
  }

  @Test
  void ignoresCompletedTasks() {
    long id = add("done already", "2026-11-01", Priority.HIGH);
    tasks.complete(id);

    assertThat(reminders.upcoming(1)).isEmpty();
  }

  @Test
  void lookAheadWidensTheSoonWindow() {
    add("in three days", "2026-11-07", Priority.LOW);

    assertThat(reminders.upcoming(1)).isEmpty();
    assertThat(reminders.upcoming(3)).hasSize(1);
    assertThat(reminders.upcoming(0)).isEmpty();
  }

  @Test
  void breaksTiesOnSameDateByPriorityThenId() {
    long low = add("low", "2026-11-04", Priority.LOW);
    long high = add("high", "2026-11-04", Priority.HIGH);

    assertThat(reminders.upcoming(0)).extracting(r -> r.task().id()).containsExactly(high, low);
  }

  @Test
  void rejectsOutOfRangeLookAhead() {
    assertThatThrownBy(() -> reminders.upcoming(-1)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> reminders.upcoming(366)).isInstanceOf(ValidationException.class);
    assertThat(reminders.upcoming(365)).isEmpty();
  }

  @Test
  void isStatelessSoRepeatedRunsGiveTheSameAnswer() {
    add("today", "2026-11-04", Priority.LOW);

    assertThat(reminders.upcoming(1)).isEqualTo(reminders.upcoming(1)).hasSize(1);
  }
}
