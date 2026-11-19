package dev.taskflow.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.taskflow.domain.ValidationException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RelativeDateTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

  @ParameterizedTest
  @CsvSource({
    "today, 2026-11-04",
    "TODAY, 2026-11-04",
    "tomorrow, 2026-11-05",
    "+3d, 2026-11-07",
    "+2w, 2026-11-18",
    "+0d, 2026-11-04",
    "'  2027-01-15 ', 2027-01-15",
    "+60d, 2027-01-03"
  })
  void parsesSupportedForms(String input, LocalDate expected) {
    assertThat(RelativeDate.parse(input, TODAY)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "someday", "2026-02-30", "11/05/2026", "+d", "+3x", "-3d", "+99999d", "yesterday"})
  void rejectsEverythingElseWithAHelpfulMessage(String input) {
    assertThatThrownBy(() -> RelativeDate.parse(input, TODAY))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("YYYY-MM-DD");
  }
}
