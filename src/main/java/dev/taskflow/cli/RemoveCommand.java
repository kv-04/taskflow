package dev.taskflow.cli;

import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "rm",
    aliases = "delete",
    mixinStandardHelpOptions = true,
    description = "Permanently delete one or more tasks.")
final class RemoveCommand extends BaseCommand {

  @Parameters(arity = "1..*", paramLabel = "ID", description = "Task id(s), as shown by 'list'.")
  private List<Long> ids;

  @Override
  public Integer call() {
    for (long id : ids) {
      app().tasks().delete(id);
      out().printf("Deleted #%d%n", id);
    }
    return ExitCodes.OK;
  }
}
