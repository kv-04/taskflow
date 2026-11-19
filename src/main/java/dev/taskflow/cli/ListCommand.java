package dev.taskflow.cli;

import dev.taskflow.application.TaskQuery;
import dev.taskflow.domain.Task;
import java.time.LocalDate;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "list",
    aliases = "ls",
    mixinStandardHelpOptions = true,
    description = "List tasks, most urgent first.")
final class ListCommand extends BaseCommand {

  @Option(
      names = "--status",
      defaultValue = "OPEN",
      description = "open, done or all (default: ${DEFAULT-VALUE}).")
  private TaskQuery.StatusFilter status;

  @Option(
      names = {"-t", "--tag"},
      paramLabel = "TAG",
      description = "Only tasks with this tag.")
  private String tag;

  @Option(names = "--overdue", description = "Only overdue tasks.")
  private boolean overdue;

  @Option(names = "--json", description = "Print JSON instead of a table.")
  private boolean json;

  @Override
  public Integer call() {
    LocalDate today = app().tasks().today();
    List<Task> tasks = app().tasks().list(new TaskQuery(status, tag, overdue));
    if (json) {
      JsonOutput.print(out(), tasks.stream().map(t -> JsonOutput.TaskView.of(t, today)).toList());
    } else {
      out().println(TaskTable.render(tasks, today, ansi()));
    }
    return ExitCodes.OK;
  }
}
