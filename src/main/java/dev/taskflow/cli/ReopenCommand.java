package dev.taskflow.cli;

import dev.taskflow.domain.Task;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "reopen",
    mixinStandardHelpOptions = true,
    description = "Mark one or more completed tasks as open again.")
final class ReopenCommand extends BaseCommand {

  @Parameters(arity = "1..*", paramLabel = "ID", description = "Task id(s), as shown by 'list'.")
  private List<Long> ids;

  @Override
  public Integer call() {
    for (long id : ids) {
      Task task = app().tasks().reopen(id);
      out().printf("Reopened #%d: %s%n", task.id(), task.title());
    }
    return ExitCodes.OK;
  }
}
