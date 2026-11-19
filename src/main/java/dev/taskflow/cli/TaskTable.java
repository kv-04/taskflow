package dev.taskflow.cli;

import dev.taskflow.application.ReminderService;
import dev.taskflow.domain.Task;
import dev.taskflow.domain.Urgency;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import picocli.CommandLine.Help.Ansi;

/**
 * Renders tasks as an aligned text table.
 *
 * <p>Colour is applied only to fixed, trusted strings (never to user-authored titles), because
 * picocli's markup syntax would otherwise let a title like {@code @|red ...|@} inject styling.
 * Padding is computed on the plain text and added outside the markup so columns stay aligned.
 */
final class TaskTable {

  private TaskTable() {}

  static String render(List<Task> tasks, LocalDate today, Ansi ansi) {
    if (tasks.isEmpty()) {
      return "No tasks.";
    }
    int idWidth =
        Math.max(2, tasks.stream().mapToInt(t -> Long.toString(t.id()).length()).max().orElse(2));
    List<String> whens = tasks.stream().map(t -> whenText(t, today)).toList();
    int whenWidth = Math.max(4, whens.stream().mapToInt(String::length).max().orElse(4));

    StringBuilder out = new StringBuilder();
    out.append(String.format("    %" + idWidth + "s  %-6s  %-10s  %-" + whenWidth + "s  %s%n",
        "ID", "PRI", "DUE", "WHEN", "TITLE"));

    for (int i = 0; i < tasks.size(); i++) {
      Task task = tasks.get(i);
      String when = whens.get(i);
      out.append(task.isDone() ? "[x] " : "[ ] ")
          .append(String.format("%" + idWidth + "d  %-6s  %-10s  ", task.id(), task.priority(),
              task.dueDate() == null ? "-" : task.dueDate().toString()))
          .append(colour(when, task.urgency(today, ReminderService.DEFAULT_SOON_DAYS), ansi))
          .append(" ".repeat(whenWidth - when.length()))
          .append("  ")
          .append(task.title());
      for (String tag : task.tags()) {
        out.append(" #").append(tag);
      }
      out.append(System.lineSeparator());
    }
    out.append(tasks.size()).append(tasks.size() == 1 ? " task" : " tasks");
    return out.toString();
  }

  static String whenText(Task task, LocalDate today) {
    if (task.isDone()) {
      return "done";
    }
    if (task.dueDate() == null) {
      return "";
    }
    long days = ChronoUnit.DAYS.between(today, task.dueDate());
    if (days < 0) {
      return "overdue " + (-days) + "d";
    }
    if (days == 0) {
      return "today";
    }
    return days == 1 ? "tomorrow" : "in " + days + "d";
  }

  private static String colour(String text, Urgency urgency, Ansi ansi) {
    if (text.isEmpty()) {
      return text;
    }
    String style =
        switch (urgency) {
          case OVERDUE -> "bold,red";
          case TODAY -> "bold,yellow";
          case SOON -> "yellow";
          case LATER, NONE -> null;
        };
    return style == null ? text : ansi.string("@|" + style + " " + text + "|@");
  }
}
