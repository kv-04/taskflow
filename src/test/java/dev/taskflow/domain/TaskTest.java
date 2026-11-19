package dev.taskflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TaskTest {

  private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

  private static Task open(String due) {
    return Task.create(
        "Write tests", Priority.MEDIUM, due == null ? null : LocalDate.parse(due), List.of(), NOW);
  }

  @Test
  void stripsTitleAndStartsOpenAndUnpersisted() {
    Task task = Task.create("  Buy milk \t", Priority.LOW, null, null, NOW);

    assertThat(task.title()).isEqualTo("Buy milk");
    assertThat(task.status()).isEqualTo(Status.OPEN);
    assertThat(task.id()).isZero();
    assertThat(task.completedAt()).isNull();
    assertThat(task.tags()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t\n"})
  void rejectsBlankTitles(String title) {
    assertThatThrownBy(() -> Task.create(title, Priority.LOW, null, null, NOW))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void rejectsNullAndOverlongTitles() {
    assertThatThrownBy(() -> Task.create(null, Priority.LOW, null, null, NOW))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Task.create("x".repeat(201), Priority.LOW, null, null, NOW))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("200");
    assertThat(Task.create("x".repeat(200), Priority.LOW, null, null, NOW).title()).hasSize(200);
  }

  @Test
  void countsCodePointsNotUtf16Units() {
    String emoji = "🚀".repeat(200); // 200 rockets = 400 chars
    assertThat(Task.create(emoji, Priority.LOW, null, null, NOW).title()).isEqualTo(emoji);
  }

  @ParameterizedTest
  @ValueSource(strings = {"a\u001b[31mred", "line1\nline2", "nul\u0000", "evil‮txt", "iso⁦late"})
  void rejectsTerminalInjectionAndBidiOverrides(String title) {
    assertThatThrownBy(() -> Task.create(title, Priority.LOW, null, null, NOW))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("control or bidirectional");
  }

  @Test
  void keepsLegitimateUnicodeAndMarkupLookingText() {
    assertThat(Task.create("Café ☕ 日本語 @|red x|@ 100%", Priority.LOW, null, null, NOW).title())
        .isEqualTo("Café ☕ 日本語 @|red x|@ 100%");
  }

  @Test
  void normalisesTagsToSortedLowercaseSet() {
    Task task = Task.create("t", Priority.LOW, null, List.of("#Work", "home", "WORK"), NOW);
    assertThat(task.tags()).containsExactly("home", "work");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "has space", "-leading", "UPPER!", "this-tag-is-far-longer-than-the-thirty-two-character-limit", "ü"})
  void rejectsInvalidTags(String tag) {
    assertThatThrownBy(() -> Task.create("t", Priority.LOW, null, List.of(tag), NOW))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("tag");
  }

  @Test
  void limitsTagCount() {
    List<String> eleven = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");
    assertThatThrownBy(() -> Task.create("t", Priority.LOW, null, eleven, NOW))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("10");
  }

  @Test
  void tagsAreImmutable() {
    Task task = Task.create("t", Priority.LOW, null, List.of("a"), NOW);
    assertThatThrownBy(() -> task.tags().add("b")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void completeAndReopenAreIdempotentAndMaintainTheCompletionInvariant() {
    Task task = open(null);
    Instant later = NOW.plusSeconds(60);

    Task done = task.complete(later);
    assertThat(done.isDone()).isTrue();
    assertThat(done.completedAt()).isEqualTo(later);
    assertThat(done.complete(later.plusSeconds(60))).isSameAs(done);

    Task reopened = done.reopen();
    assertThat(reopened.isDone()).isFalse();
    assertThat(reopened.completedAt()).isNull();
    assertThat(reopened.reopen()).isSameAs(reopened);
  }

  @Test
  void rejectsInconsistentCompletionState() {
    assertThatThrownBy(
            () -> new Task(1, "t", Status.DONE, Priority.LOW, null, Set.of(), NOW, null))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(
            () -> new Task(1, "t", Status.OPEN, Priority.LOW, null, Set.of(), NOW, NOW))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> new Task(-1, "t", Status.OPEN, Priority.LOW, null, Set.of(), NOW, null))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> new Task(1, "t", null, Priority.LOW, null, Set.of(), NOW, null))
        .isInstanceOf(ValidationException.class);
  }

  @ParameterizedTest(name = "due {0}, soon={1} -> {2}")
  @CsvSource({
    "2026-11-03, 1, OVERDUE",
    "2025-01-01, 1, OVERDUE",
    "2026-11-04, 1, TODAY",
    "2026-11-05, 1, SOON",
    "2026-11-06, 1, LATER",
    "2026-11-05, 0, LATER",
    "2026-11-07, 3, SOON",
    "2026-11-08, 3, LATER"
  })
  void classifiesUrgencyAtTheBoundaries(String due, int soonDays, Urgency expected) {
    assertThat(open(due).urgency(TODAY, soonDays)).isEqualTo(expected);
  }

  @Test
  void doneOrUndatedTasksHaveNoUrgency() {
    assertThat(open(null).urgency(TODAY, 1)).isEqualTo(Urgency.NONE);
    assertThat(open("2020-01-01").complete(NOW).urgency(TODAY, 1)).isEqualTo(Urgency.NONE);
  }

  @Test
  void urgencyActionability() {
    assertThat(Urgency.OVERDUE.isActionable()).isTrue();
    assertThat(Urgency.TODAY.isActionable()).isTrue();
    assertThat(Urgency.SOON.isActionable()).isTrue();
    assertThat(Urgency.LATER.isActionable()).isFalse();
    assertThat(Urgency.NONE.isActionable()).isFalse();
  }
}
