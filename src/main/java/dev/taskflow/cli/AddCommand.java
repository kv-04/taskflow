package dev.taskflow.cli;

import dev.taskflow.domain.Priority;
import dev.taskflow.domain.Task;
import java.time.LocalDate;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "add",
    mixinStandardHelpOptions = true,
    description = "Add a task.")
final class AddCommand extends BaseCommand {

  @Parameters(index = "0", paramLabel = "TITLE", description = "What needs doing.")
  private String title;

  @Option(
      names = {"-d", "--due"},
      paramLabel = "DATE",
      description = "Due date: YYYY-MM-DD, today, tomorrow, +3d or +2w.")
  private String due;

  @Option(
      names = {"-p", "--priority"},
      defaultValue = "MEDIUM",
      description = "low, medium or high (default: ${DEFAULT-VALUE}).")
  private Priority priority;

  @Option(
      names = {"-t", "--tag"},
      split = ",",
      paramLabel = "TAG",
      description = "Tag the task. Repeat the option or comma-separate.")
  private List<String> tags;

  @Option(names = "--json", description = "Print the created task as JSON.")
  private boolean json;

  @Override
  public Integer call() {
    LocalDate today = app().tasks().today();
    LocalDate dueDate = due == null ? null : RelativeDate.parse(due, today);
    Task task = app().tasks().add(title, priority, dueDate, tags);
    if (json) {
      JsonOutput.print(out(), JsonOutput.TaskView.of(task, today));
    } else {
      out().printf("Added #%d: %s%n", task.id(), task.title());
    }
    return ExitCodes.OK;
  }
}
