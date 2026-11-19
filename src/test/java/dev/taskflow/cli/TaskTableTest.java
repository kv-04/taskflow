package dev.taskflow.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.taskflow.domain.Priority;
import dev.taskflow.domain.Task;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Help.Ansi;

class TaskTableTest {

  private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

  private static Task task(long id, String title, String due, String... tags) {
    return Task.create(
            title, Priority.HIGH, due == null ? null : LocalDate.parse(due), List.of(tags), NOW)
        .withId(id);
  }

  @Test
  void emptyListSaysSo() {
    assertThat(TaskTable.render(List.of(), TODAY, Ansi.OFF)).isEqualTo("No tasks.");
  }

  @Test
  void rendersAlignedColumnsTagsAndCount() {
    String table =
        TaskTable.render(
            List.of(task(1, "Buy milk", "2026-11-05", "home"), task(12, "Pay rent", "2026-11-01")),
            TODAY,
            Ansi.OFF);

    assertThat(table).contains("ID", "PRI", "DUE", "WHEN", "TITLE");
    assertThat(table).contains("Buy milk #home").contains("tomorrow");
    assertThat(table).contains("Pay rent").contains("overdue 3d");
    assertThat(table).endsWith("2 tasks");
  }

  @Test
  void describesRelativeDueDates() {
    assertThat(TaskTable.whenText(task(1, "t", "2026-11-04"), TODAY)).isEqualTo("today");
    assertThat(TaskTable.whenText(task(1, "t", "2026-11-05"), TODAY)).isEqualTo("tomorrow");
    assertThat(TaskTable.whenText(task(1, "t", "2026-11-14"), TODAY)).isEqualTo("in 10d");
    assertThat(TaskTable.whenText(task(1, "t", "2026-11-03"), TODAY)).isEqualTo("overdue 1d");
    assertThat(TaskTable.whenText(task(1, "t", null), TODAY)).isEmpty();
    assertThat(TaskTable.whenText(task(1, "t", "2026-11-03").complete(NOW), TODAY)).isEqualTo("done");
  }

  @Test
  void singularCount() {
    assertThat(TaskTable.render(List.of(task(1, "x", null)), TODAY, Ansi.OFF)).endsWith("1 task");
  }

  @Test
  void colourIsAppliedOnlyToTrustedTextNeverUserTitles() {
    String hostile = "@|red,bold pwned|@";
    String table = TaskTable.render(List.of(task(1, hostile, "2026-11-01")), TODAY, Ansi.ON);

    // The hostile title survives verbatim (it was not interpreted as markup)...
    assertThat(table).contains(hostile);
    // ...while our own overdue label was styled.
    assertThat(table).contains("\u001b[");
  }
}
