package dev.taskflow.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.taskflow.application.ReminderService.Reminder;
import dev.taskflow.application.ReminderService;
import dev.taskflow.domain.Priority;
import dev.taskflow.domain.Status;
import dev.taskflow.domain.Task;
import dev.taskflow.domain.Urgency;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * Machine-readable output ({@code --json}). The shapes below are a public contract for scripts, so
 * they are explicit records rather than whatever the domain model happens to serialise to.
 */
final class JsonOutput {

  record TaskView(
      long id,
      String title,
      Status status,
      Priority priority,
      LocalDate dueDate,
      Urgency urgency,
      Set<String> tags,
      Instant createdAt,
      Instant completedAt) {

    static TaskView of(Task task, LocalDate today) {
      return new TaskView(
          task.id(),
          task.title(),
          task.status(),
          task.priority(),
          task.dueDate(),
          task.urgency(today, ReminderService.DEFAULT_SOON_DAYS),
          task.tags(),
          task.createdAt(),
          task.completedAt());
    }
  }

  record ReminderView(TaskView task, Urgency urgency, long daysUntilDue) {
    static ReminderView of(Reminder reminder, LocalDate today) {
      return new ReminderView(
          TaskView.of(reminder.task(), today), reminder.urgency(), reminder.daysUntilDue());
    }
  }

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .enable(SerializationFeature.INDENT_OUTPUT);

  private JsonOutput() {}

  static void print(PrintWriter out, Object value) {
    try {
      out.println(MAPPER.writeValueAsString(value));
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
