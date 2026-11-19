package dev.taskflow.cli;

import dev.taskflow.application.ReminderService;
import dev.taskflow.application.TaskService;
import dev.taskflow.domain.TaskFlowException;
import dev.taskflow.infrastructure.DataDirResolver;
import dev.taskflow.infrastructure.JsonFileTaskRepository;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Composition root and root command.
 *
 * <p>Everything environmental -- clock, environment variables, home directory, output streams -- is
 * passed in, never read from statics. That is what lets the end-to-end tests drive the real CLI
 * deterministically and in parallel.
 */
@Command(
    name = "taskflow",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    description = "A fast, scriptable, crash-safe task manager.",
    subcommands = {
      AddCommand.class,
      ListCommand.class,
      DoneCommand.class,
      ReopenCommand.class,
      RemoveCommand.class,
      RemindCommand.class,
      ImportLegacyCommand.class
    },
    footer = {
      "",
      "Exit codes: 0 ok | 2 usage error | 3 task not found | 4 invalid input | 70 internal error | 74 storage I/O error",
      "Data location: --data-dir, else $TASKFLOW_HOME, else $XDG_DATA_HOME/taskflow, else ~/.local/share/taskflow"
    })
public final class TaskFlowCli implements Callable<Integer> {

  private static final Logger LOG = LoggerFactory.getLogger(TaskFlowCli.class);

  @Option(
      names = "--data-dir",
      paramLabel = "DIR",
      scope = ScopeType.INHERIT,
      description = "Directory holding task data.")
  private Path dataDir;

  @Option(
      names = {"-v", "--verbose"},
      scope = ScopeType.INHERIT,
      description = "Log to stderr. Repeat (-vv) for debug detail.")
  private boolean[] verbosity = new boolean[0];

  @Spec private CommandSpec spec;

  private final Clock clock;
  private final Map<String, String> env;
  private final Path home;
  private AppContext context;

  private TaskFlowCli(Clock clock, Map<String, String> env, Path home) {
    this.clock = clock;
    this.env = env;
    this.home = home;
  }

  /** Builds a fully configured command line. This is the single wiring point of the application. */
  public static CommandLine create(
      Clock clock, Map<String, String> env, Path home, PrintWriter out, PrintWriter err) {
    TaskFlowCli cli = new TaskFlowCli(clock, env, home);
    CommandLine commandLine = new CommandLine(cli);
    commandLine.setOut(out);
    commandLine.setErr(err);
    commandLine.setCaseInsensitiveEnumValuesAllowed(true);
    // Security: with @-file expansion on, a title such as "@/etc/passwd" would be read as a file.
    commandLine.setExpandAtFiles(false);
    commandLine.setExecutionStrategy(cli::executeObserved);
    commandLine.setExecutionExceptionHandler(
        (exception, line, parseResult) -> cli.handleFailure(exception, line));
    return commandLine;
  }

  @Override
  public Integer call() {
    spec.commandLine().usage(spec.commandLine().getErr());
    return ExitCodes.USAGE;
  }

  synchronized AppContext context() {
    if (context == null) {
      Path directory = DataDirResolver.resolve(dataDir, env, home);
      JsonFileTaskRepository repository = new JsonFileTaskRepository(directory.resolve("tasks.json"));
      context =
          new AppContext(
              new TaskService(repository, clock), new ReminderService(repository, clock), clock);
      LOG.debug("storage_ready directory={}", directory);
    }
    return context;
  }

  /**
   * Wraps every invocation with observability: a correlation id and command name on each log line
   * (via MDC), the requested verbosity, and a duration/exit-code summary.
   */
  private int executeObserved(ParseResult parseResult) {
    ParseResult leaf = parseResult;
    while (leaf.hasSubcommand()) {
      leaf = leaf.subcommand();
    }
    MDC.put("command", leaf.commandSpec().name());
    MDC.put("command_id", UUID.randomUUID().toString().substring(0, 8));
    Runnable restoreLogging = LoggingConfig.apply(verbosity.length);
    long started = System.nanoTime();
    int exitCode = ExitCodes.INTERNAL;
    try {
      exitCode = new CommandLine.RunLast().execute(parseResult);
      return exitCode;
    } finally {
      LOG.info(
          "command_finished exit_code={} duration_ms={}",
          exitCode,
          (System.nanoTime() - started) / 1_000_000);
      restoreLogging.run();
      MDC.clear();
    }
  }

  private int handleFailure(Exception exception, CommandLine line) {
    PrintWriter err = line.getErr();
    if (exception instanceof TaskFlowException expected) {
      int code = ExitCodes.of(expected);
      LOG.warn("command_failed type={} exit_code={}", expected.getClass().getSimpleName(), code);
      err.println("error: " + expected.getMessage());
      return code;
    }
    // Anything else is a bug: log the stack trace, show the user a short, non-leaky message.
    LOG.error("unexpected_failure", exception);
    err.println("error: unexpected internal error (re-run with -vv for details)");
    return ExitCodes.INTERNAL;
  }
}
