package dev.taskflow.cli;

import java.io.PrintWriter;
import java.util.concurrent.Callable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** Shared plumbing for subcommands: access to wiring and to the (injectable) output streams. */
abstract class BaseCommand implements Callable<Integer> {

  @ParentCommand private TaskFlowCli root;
  @Spec private CommandSpec spec;

  protected final AppContext app() {
    return root.context();
  }

  /** Standard output: command results only, so it is always safe to pipe. */
  protected final PrintWriter out() {
    return spec.commandLine().getOut();
  }

  protected final PrintWriter err() {
    return spec.commandLine().getErr();
  }

  protected final picocli.CommandLine.Help.Ansi ansi() {
    return spec.commandLine().getColorScheme().ansi();
  }
}
