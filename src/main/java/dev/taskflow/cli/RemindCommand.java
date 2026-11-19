package dev.taskflow.cli;

import dev.taskflow.application.ReminderService;
import dev.taskflow.application.ReminderService.Reminder;
import java.time.LocalDate;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "remind",
    mixinStandardHelpOptions = true,
    description =
        "Show overdue, due-today and soon-due tasks. Handy in a shell profile or a cron job.")
final class RemindCommand extends BaseCommand {

  @Option(
      names = "--within-days",
      paramLabel = "N",
      defaultValue = "" + ReminderService.DEFAULT_SOON_DAYS,
      description = "Also include tasks due within N days (default: ${DEFAULT-VALUE}).")
  private int withinDays;

  @Option(names = "--json", description = "Print JSON instead of text.")
  private boolean json;

  @Override
  public Integer call() {
    LocalDate today = app().tasks().today();
    List<Reminder> reminders = app().reminders().upcoming(withinDays);

    if (json) {
      JsonOutput.print(out(), reminders.stream().map(r -> JsonOutput.ReminderView.of(r, today)).toList());
      return ExitCodes.OK;
    }
    if (reminders.isEmpty()) {
      out().println("Nothing due. You're all caught up.");
      return ExitCodes.OK;
    }
    for (Reminder reminder : reminders) {
      String label = reminder.urgency().name();
      String styled =
          ansi().string("@|" + styleFor(reminder) + " " + label + "|@")
              + " ".repeat(Math.max(0, 8 - label.length()));
      out().printf(
          "%s #%d %s (%s)%n",
          styled, reminder.task().id(), reminder.task().title(), describe(reminder));
    }
    return ExitCodes.OK;
  }

  private static String styleFor(Reminder reminder) {
    return switch (reminder.urgency()) {
      case OVERDUE -> "bold,red";
      case TODAY -> "bold,yellow";
      default -> "yellow";
    };
  }

  private static String describe(Reminder reminder) {
    long days = reminder.daysUntilDue();
    return switch (reminder.urgency()) {
      case OVERDUE -> "due " + reminder.task().dueDate() + ", " + (-days) + (days == -1 ? " day" : " days") + " overdue";
      case TODAY -> "due today";
      default -> days == 1 ? "due tomorrow" : "due in " + days + " days";
    };
  }
}
