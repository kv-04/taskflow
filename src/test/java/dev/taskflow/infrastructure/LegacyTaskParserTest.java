package dev.taskflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.taskflow.domain.Priority;
import dev.taskflow.domain.Task;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyTaskParserTest {

  private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

  @Test
  void parsesTheOriginalPrototypeSampleData() {
    LegacyTaskParser.Result result =
        LegacyTaskParser.parse(
            List.of("shopping|2025-11-09|false", "studying |2026-02-12|false", ""), NOW);

    assertThat(result.warnings()).isEmpty();
    assertThat(result.tasks())
        .extracting(Task::title, Task::dueDate, Task::isDone, Task::priority)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "shopping", LocalDate.of(2025, 11, 9), false, Priority.MEDIUM),
            org.assertj.core.groups.Tuple.tuple(
                "studying", LocalDate.of(2026, 2, 12), false, Priority.MEDIUM));
  }

  @Test
  void supportsNullDueDatesAndCompletedTasks() {
    List<Task> tasks = LegacyTaskParser.parse(List.of("call mum|null|true"), NOW).tasks();

    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0).dueDate()).isNull();
    assertThat(tasks.get(0).isDone()).isTrue();
    assertThat(tasks.get(0).completedAt()).isEqualTo(NOW);
  }

  @Test
  void pipesInsideDescriptionsSurviveBecauseFieldsAreReadFromTheRight() {
    List<Task> tasks = LegacyTaskParser.parse(List.of("a|b|c|2026-01-01|true"), NOW).tasks();

    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0).title()).isEqualTo("a|b|c");
    assertThat(tasks.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 1, 1));
  }

  @Test
  void reportsEachBadLineWithItsNumberAndKeepsTheGoodOnes() {
    LegacyTaskParser.Result result =
        LegacyTaskParser.parse(
            List.of(
                "good|null|false",
                "no separators",
                "x|2026-13-45|false",
                "y|null|maybe",
                "   |null|false",
                "another good|null|false"),
            NOW);

    assertThat(result.tasks()).extracting(Task::title).containsExactly("good", "another good");
    assertThat(result.warnings())
        .hasSize(4)
        .anyMatch(w -> w.startsWith("line 2:"))
        .anyMatch(w -> w.startsWith("line 3:") && w.contains("invalid due date"))
        .anyMatch(w -> w.startsWith("line 4:") && w.contains("true or false"))
        .anyMatch(w -> w.startsWith("line 5:") && w.contains("blank"));
  }

  @Test
  void neverEchoesControlCharactersFromUntrustedFiles() {
    LegacyTaskParser.Result result =
        LegacyTaskParser.parse(List.of("x|\u001b[31mbad\u001b[0m|false"), NOW);

    assertThat(result.warnings()).hasSize(1);
    assertThat(result.warnings().get(0)).doesNotContain("\u001b");
  }

  @Test
  void rejectsLegacyTitlesThatContainTerminalEscapes() {
    LegacyTaskParser.Result result =
        LegacyTaskParser.parse(List.of("evil\u001b[2J|null|false"), NOW);

    assertThat(result.tasks()).isEmpty();
    assertThat(result.warnings()).hasSize(1);
  }
}
