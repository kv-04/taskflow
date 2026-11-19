package dev.taskflow.infrastructure;

import dev.taskflow.domain.Priority;
import dev.taskflow.domain.Task;
import dev.taskflow.domain.TaskFlowException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the v0 prototype's {@code description|dueDate|completed} line format so existing data can
 * be migrated.
 *
 * <p>The old format split on every {@code |}, so a description containing one silently lost data.
 * This parser reads the two trailing fields from the <em>right</em>, which makes pipes in
 * descriptions safe. Bad lines are reported with their line number and skipped; one bad line never
 * aborts the whole import.
 */
public final class LegacyTaskParser {

  public record Result(List<Task> tasks, List<String> warnings) {}

  private LegacyTaskParser() {}

  public static Result parse(List<String> lines, Instant now) {
    List<Task> tasks = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.isBlank()) {
        continue;
      }
      int lineNumber = i + 1;
      try {
        tasks.add(parseLine(line, now));
      } catch (TaskFlowException | IllegalArgumentException e) {
        warnings.add("line " + lineNumber + ": " + e.getMessage());
      }
    }
    return new Result(List.copyOf(tasks), List.copyOf(warnings));
  }

  private static Task parseLine(String line, Instant now) {
    int last = line.lastIndexOf('|');
    int secondLast = last <= 0 ? -1 : line.lastIndexOf('|', last - 1);
    if (secondLast < 0) {
      throw new IllegalArgumentException("expected 'description|due-date|completed'");
    }
    String description = line.substring(0, secondLast);
    String dueText = line.substring(secondLast + 1, last).strip();
    String completedText = line.substring(last + 1).strip();

    LocalDate due = parseDue(dueText);
    boolean completed = parseCompleted(completedText);

    Task task = Task.create(description, Priority.MEDIUM, due, List.of(), now);
    return completed ? task.complete(now) : task;
  }

  private static LocalDate parseDue(String text) {
    if (text.equals("null") || text.isEmpty()) {
      return null;
    }
    try {
      return LocalDate.parse(text);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("invalid due date '" + sanitize(text) + "'");
    }
  }

  private static boolean parseCompleted(String text) {
    return switch (text.toLowerCase(java.util.Locale.ROOT)) {
      case "true" -> true;
      case "false" -> false;
      default -> throw new IllegalArgumentException("completed flag must be true or false");
    };
  }

  /** Keeps untrusted file content from injecting control characters into our own error output. */
  private static String sanitize(String text) {
    String shortened = text.length() > 30 ? text.substring(0, 30) + "..." : text;
    return shortened.replaceAll("\\p{Cntrl}", "?");
  }
}
