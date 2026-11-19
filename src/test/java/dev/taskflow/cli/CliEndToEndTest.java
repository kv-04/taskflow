package dev.taskflow.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;

/**
 * Drives the real command line -- argument parsing, wiring, JSON storage on disk, exit codes --
 * with only the clock and environment injected. These tests are the executable specification of the
 * CLI's contract.
 */
class CliEndToEndTest {

  /** "Today" is 2026-11-04 in every test. */
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);

  @TempDir Path dir;

  private String out;
  private String err;

  @BeforeEach
  void resetOutput() {
    out = "";
    err = "";
  }

  /** Runs one invocation of the CLI against {@code dir} and captures both streams. */
  private int run(String... args) {
    return runWithHome(dir, args);
  }

  private int runWithHome(Path taskflowHome, String... args) {
    StringWriter outBuffer = new StringWriter();
    StringWriter errBuffer = new StringWriter();
    CommandLine cli =
        TaskFlowCli.create(
            CLOCK,
            Map.of("TASKFLOW_HOME", taskflowHome.toString()),
            dir,
            new PrintWriter(outBuffer),
            new PrintWriter(errBuffer));
    cli.setColorScheme(CommandLine.Help.defaultColorScheme(Ansi.OFF));
    int exitCode = cli.execute(args);
    out = outBuffer.toString();
    err = errBuffer.toString();
    return exitCode;
  }

  @Test
  void addThenListShowsTheTask() {
    assertThat(run("add", "Buy milk", "--due", "tomorrow", "-p", "high", "-t", "home,errand"))
        .isZero();
    assertThat(out).contains("Added #1: Buy milk");

    assertThat(run("list")).isZero();
    assertThat(out)
        .contains("Buy milk")
        .contains("HIGH")
        .contains("2026-11-05")
        .contains("tomorrow")
        .contains("#errand")
        .contains("#home")
        .endsWith("1 task" + System.lineSeparator());
  }

  @Test
  void listWithNoTasksSaysSo() {
    assertThat(run("list")).isZero();
    assertThat(out.trim()).isEqualTo("No tasks.");
  }

  @Test
  void jsonOutputIsStableAndMachineReadable() {
    run("add", "Ship it", "--due", "2026-11-01", "-p", "low");

    assertThat(run("list", "--json")).isZero();

    assertThat(out)
        .contains("\"id\" : 1")
        .contains("\"title\" : \"Ship it\"")
        .contains("\"status\" : \"OPEN\"")
        .contains("\"priority\" : \"LOW\"")
        .contains("\"dueDate\" : \"2026-11-01\"")
        .contains("\"urgency\" : \"OVERDUE\"")
        .contains("\"createdAt\" : \"2026-11-04T12:00:00Z\"");
    assertThat(err).isEmpty();
  }

  @Test
  void addCanEmitJson() {
    assertThat(run("add", "Thing", "--json")).isZero();
    assertThat(out).contains("\"id\" : 1").contains("\"title\" : \"Thing\"");
  }

  @Test
  void doneHidesTheTaskFromTheDefaultViewButKeepsItInHistory() {
    run("add", "Write report");

    assertThat(run("done", "1")).isZero();
    assertThat(out).contains("Completed #1: Write report");

    run("list");
    assertThat(out.trim()).isEqualTo("No tasks.");

    run("list", "--status", "done");
    assertThat(out).contains("[x]").contains("Write report");

    assertThat(run("reopen", "1")).isZero();
    run("list");
    assertThat(out).contains("[ ]").contains("Write report");
  }

  @Test
  void removeDeletesAndIdsAreNotReused() {
    run("add", "first");
    run("add", "second");

    assertThat(run("rm", "2")).isZero();
    assertThat(out).contains("Deleted #2");

    run("add", "third");
    assertThat(out).contains("Added #3: third");
  }

  @Test
  void aliasesWork() {
    run("add", "x");
    assertThat(run("ls")).isZero();
    assertThat(run("complete", "1")).isZero();
    assertThat(run("delete", "1")).isZero();
  }

  @Test
  void unknownIdExitsWithNotFoundCodeAndMessageOnStderr() {
    assertThat(run("done", "42")).isEqualTo(ExitCodes.NOT_FOUND);
    assertThat(err).contains("error: No task with id 42");
    assertThat(out).isEmpty();

    assertThat(run("rm", "42")).isEqualTo(ExitCodes.NOT_FOUND);
  }

  @Test
  void invalidInputExitsWithInvalidInputCode() {
    assertThat(run("add", "   ")).isEqualTo(ExitCodes.INVALID_INPUT);
    assertThat(err).contains("error:").contains("blank");

    assertThat(run("add", "x", "--due", "someday")).isEqualTo(ExitCodes.INVALID_INPUT);
    assertThat(err).contains("Invalid date");

    assertThat(run("add", "x", "-t", "Bad Tag!")).isEqualTo(ExitCodes.INVALID_INPUT);
    assertThat(run("add", "bad\u001b[31m")).isEqualTo(ExitCodes.INVALID_INPUT);
    assertThat(run("list", "--tag", "not valid")).isEqualTo(ExitCodes.INVALID_INPUT);
    assertThat(run("remind", "--within-days", "9999")).isEqualTo(ExitCodes.INVALID_INPUT);
  }

  @Test
  void usageErrorsExitWithUsageCode() {
    assertThat(run("add")).isEqualTo(ExitCodes.USAGE); // missing TITLE
    assertThat(run("nonsense")).isEqualTo(ExitCodes.USAGE);
    assertThat(run("done", "abc")).isEqualTo(ExitCodes.USAGE); // not a number
    assertThat(run("add", "x", "-p", "urgent")).isEqualTo(ExitCodes.USAGE); // not a priority
    assertThat(run()).isEqualTo(ExitCodes.USAGE); // no command: usage printed
    assertThat(err).contains("Usage: taskflow");
  }

  @Test
  void priorityAndStatusAreCaseInsensitive() {
    assertThat(run("add", "x", "-p", "High")).isZero();
    assertThat(run("list", "--status", "ALL")).isZero();
  }

  @Test
  void titleBeginningWithAtSignIsNotTreatedAsAnArgumentFile() {
    Path secret = dir.resolve("secret.txt");
    try {
      Files.writeString(secret, "--due\nsomeday\n");
    } catch (IOException e) {
      throw new AssertionError(e);
    }

    assertThat(run("add", "@" + secret)).isZero();
    assertThat(out).contains("Added #1: @" + secret);
  }

  @Test
  void remindShowsOverdueTodayAndTomorrowButNotLaterTasks() {
    run("add", "Overdue", "--due", "2026-11-01");
    run("add", "Today", "--due", "today");
    run("add", "Tomorrow", "--due", "tomorrow");
    run("add", "Later", "--due", "+5d");
    run("add", "Undated");

    assertThat(run("remind")).isZero();

    assertThat(out)
        .contains("OVERDUE")
        .contains("Overdue")
        .contains("3 days overdue")
        .contains("TODAY")
        .contains("due today")
        .contains("SOON")
        .contains("due tomorrow")
        .doesNotContain("Later")
        .doesNotContain("Undated");
  }

  @Test
  void remindLookAheadAndEmptyState() {
    assertThat(run("remind")).isZero();
    assertThat(out).contains("Nothing due");

    run("add", "In five days", "--due", "+5d");
    run("remind");
    assertThat(out).contains("Nothing due");

    run("remind", "--within-days", "5");
    assertThat(out).contains("In five days").contains("due in 5 days");
  }

  @Test
  void remindEmitsJson() {
    run("add", "Overdue", "--due", "2026-11-01");

    assertThat(run("remind", "--json")).isZero();

    assertThat(out).contains("\"urgency\" : \"OVERDUE\"").contains("\"daysUntilDue\" : -3");
  }

  @Test
  void overdueFilter() {
    run("add", "late", "--due", "2026-11-01");
    run("add", "fine", "--due", "2027-01-01");

    run("list", "--overdue");

    assertThat(out).contains("late").doesNotContain("fine");
  }

  @Test
  void tagFilterIsCaseInsensitive() {
    run("add", "a", "-t", "work");
    run("add", "b", "-t", "home");

    run("list", "--tag", "#WORK");

    assertThat(out).contains("#work").doesNotContain("#home");
  }

  @Test
  void importLegacyMigratesThePrototypeFormatIdempotently() throws IOException {
    Path legacy = dir.resolve("tasks.txt");
    Files.writeString(
        legacy, "shopping|2025-11-09|false\nstudying |2026-02-12|false\nbroken line\n");

    assertThat(run("import-legacy", "--dry-run", legacy.toString())).isZero();
    assertThat(out).contains("Dry run: would import 2 task(s); 1 line(s) rejected.");
    run("list");
    assertThat(out.trim()).isEqualTo("No tasks.");

    assertThat(run("import-legacy", legacy.toString())).isZero();
    assertThat(out).contains("Imported 2 task(s), skipped 0 duplicate(s), rejected 1 line(s).");
    assertThat(err).contains("warning: line 3:");

    assertThat(run("import-legacy", legacy.toString())).isZero();
    assertThat(out).contains("Imported 0 task(s), skipped 2 duplicate(s)");

    run("list", "--overdue");
    assertThat(out).contains("shopping").contains("studying").contains("overdue");
  }

  @Test
  void importLegacyReportsMissingFilesAsStorageErrors() {
    assertThat(run("import-legacy", dir.resolve("missing.txt").toString()))
        .isEqualTo(ExitCodes.IO);
    assertThat(err).contains("Cannot read legacy file");
  }

  @Test
  void corruptDataFileExitsWithIoCodeAndIsPreserved() throws IOException {
    Path data = dir.resolve("tasks.json");
    Files.writeString(data, "{ nope");

    assertThat(run("list")).isEqualTo(ExitCodes.IO);
    assertThat(err).contains("corrupt");
    assertThat(run("add", "x")).isEqualTo(ExitCodes.IO);
    assertThat(Files.readString(data)).isEqualTo("{ nope");
  }

  @Test
  void dataDirOptionOverridesTheEnvironmentEvenAfterTheSubcommand() throws IOException {
    Path elsewhere = dir.resolve("elsewhere");

    assertThat(run("add", "x", "--data-dir", elsewhere.toString())).isZero();

    assertThat(Files.exists(elsewhere.resolve("tasks.json"))).isTrue();
    assertThat(Files.exists(dir.resolve("tasks.json"))).isFalse();
  }

  @Test
  void verboseFlagIsAcceptedAnywhere() {
    assertThat(run("-v", "add", "x")).isZero();
    assertThat(run("list", "-vv")).isZero();
    assertThat(out).contains("x");
  }

  @Test
  void helpAndVersionWork() {
    assertThat(run("--help")).isZero();
    assertThat(out).contains("Usage: taskflow").contains("Exit codes:").contains("add").contains("remind");

    assertThat(run("--version")).isZero();
    assertThat(out).contains("taskflow");

    assertThat(run("add", "--help")).isZero();
    assertThat(out).contains("--due").contains("--priority");
  }

  @Test
  void aTaskTitleContainingFormatCharactersIsPrintedLiterally() {
    assertThat(run("add", "100% sure %s %n @|red x|@")).isZero();
    assertThat(out).contains("Added #1: 100% sure %s %n @|red x|@");
  }
}
