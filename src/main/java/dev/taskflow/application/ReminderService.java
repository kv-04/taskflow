package dev.taskflow.application;

import dev.taskflow.domain.Priority;
import dev.taskflow.domain.Task;
import dev.taskflow.domain.Urgency;
import dev.taskflow.domain.ValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes which tasks need attention.
 *
 * <p>Deliberately <em>pure</em>: it returns data and prints nothing, so it can be unit-tested
 * without capturing stdout and rendered however the caller likes (text, JSON, a notification). It
 * is also stateless -- there is no in-memory "already reminded" set to lose between runs, so
 * {@code taskflow remind} behaves identically every time it is invoked, e.g. from a shell profile
 * or cron.
 */
public final class ReminderService {

  public static final int MAX_LOOKAHEAD_DAYS = 365;

  /** "Soon" means due tomorrow, matching the original prototype's behaviour. */
  public static final int DEFAULT_SOON_DAYS = 1;

  /**
   * @param daysUntilDue negative when overdue, 0 when due today
   */
  public record Reminder(Task task, Urgency urgency, long daysUntilDue) {}

  private static final Comparator<Reminder> ORDER =
      Comparator.comparing((Reminder r) -> r.task().dueDate())
          .thenComparing(r -> r.task().priority(), Comparator.<Priority>reverseOrder())
          .thenComparingLong(r -> r.task().id());

  private final TaskRepository repository;
  private final Clock clock;

  public ReminderService(TaskRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * Overdue, due-today and soon-due open tasks, most urgent first.
   *
   * @param soonDays how many days ahead counts as "soon" (0 = only overdue and today)
   */
  public List<Reminder> upcoming(int soonDays) {
    if (soonDays < 0 || soonDays > MAX_LOOKAHEAD_DAYS) {
      throw new ValidationException(
          "Look-ahead must be between 0 and " + MAX_LOOKAHEAD_DAYS + " days");
    }
    LocalDate today = LocalDate.now(clock);
    List<Reminder> reminders = new ArrayList<>();
    for (Task task : repository.findAll()) {
      Urgency urgency = task.urgency(today, soonDays);
      if (urgency.isActionable()) {
        reminders.add(
            new Reminder(task, urgency, ChronoUnit.DAYS.between(today, task.dueDate())));
      }
    }
    reminders.sort(ORDER);
    return List.copyOf(reminders);
  }
}
