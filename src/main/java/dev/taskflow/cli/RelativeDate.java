package dev.taskflow.cli;

import dev.taskflow.domain.ValidationException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses {@code YYYY-MM-DD}, {@code today}, {@code tomorrow}, {@code +3d} and {@code +2w}. */
final class RelativeDate {

  private static final Pattern OFFSET = Pattern.compile("\\+(\\d{1,4})([dw])");
  private static final String HELP =
      "Invalid date. Use YYYY-MM-DD, today, tomorrow, +<n>d (days) or +<n>w (weeks)";

  private RelativeDate() {}

  static LocalDate parse(String raw, LocalDate today) {
    String text = raw.strip().toLowerCase(Locale.ROOT);
    switch (text) {
      case "today":
        return today;
      case "tomorrow":
        return today.plusDays(1);
      default:
        break;
    }
    Matcher offset = OFFSET.matcher(text);
    if (offset.matches()) {
      long amount = Long.parseLong(offset.group(1));
      return today.plusDays("w".equals(offset.group(2)) ? amount * 7 : amount);
    }
    try {
      return LocalDate.parse(text);
    } catch (DateTimeParseException e) {
      throw new ValidationException(HELP);
    }
  }
}
