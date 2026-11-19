package dev.taskflow.cli;

import dev.taskflow.application.TaskService.ImportResult;
import dev.taskflow.domain.StorageException;
import dev.taskflow.infrastructure.LegacyTaskParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "import-legacy",
    mixinStandardHelpOptions = true,
    description =
        "Import tasks from the v0 prototype's tasks.txt ('description|due-date|completed' lines). "
            + "Safe to re-run: duplicates are skipped.")
final class ImportLegacyCommand extends BaseCommand {

  private static final long MAX_LEGACY_BYTES = 1024 * 1024;

  @Parameters(index = "0", paramLabel = "FILE", description = "Path to the legacy tasks.txt.")
  private Path file;

  @Option(names = "--dry-run", description = "Parse and report, but write nothing.")
  private boolean dryRun;

  @Override
  public Integer call() {
    List<String> lines = readLines(file);
    LegacyTaskParser.Result parsed =
        LegacyTaskParser.parse(lines, app().clock().instant().truncatedTo(ChronoUnit.SECONDS));

    parsed.warnings().forEach(warning -> err().println("warning: " + warning));

    if (dryRun) {
      out().printf(
          "Dry run: would import %d task(s); %d line(s) rejected.%n",
          parsed.tasks().size(), parsed.warnings().size());
      return ExitCodes.OK;
    }
    ImportResult result = app().tasks().importTasks(parsed.tasks());
    out().printf(
        "Imported %d task(s), skipped %d duplicate(s), rejected %d line(s).%n",
        result.added(), result.duplicates(), parsed.warnings().size());
    return ExitCodes.OK;
  }

  private static List<String> readLines(Path file) {
    try {
      if (Files.size(file) > MAX_LEGACY_BYTES) {
        throw new StorageException("Legacy file is larger than " + MAX_LEGACY_BYTES + " bytes");
      }
      return Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new StorageException("Cannot read legacy file " + file + ": " + e.getMessage(), e);
    }
  }
}
