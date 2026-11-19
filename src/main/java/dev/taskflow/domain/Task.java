package dev.taskflow.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * An immutable task. Every instance is valid: invariants are enforced in the canonical constructor,
 * so no code path (including deserialisation of a hand-edited file) can produce a malformed task.
 *
 * <p>Time never comes from a static clock here; callers pass {@code now}/{@code today} in, which
 * makes all date logic deterministic and trivially testable.
 *
 * @param id positive once persisted; {@code 0} means "not yet persisted"
 */
public record Task(
    long id,
    String title,
    Status status,
    Priority priority,
    LocalDate dueDate,
    Set<String> tags,
    Instant createdAt,
    Instant completedAt) {

  public static final int MAX_TITLE_LENGTH = 200;
  public static final int MAX_TAGS = 10;
  private static final Pattern TAG_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

  public Task {
    if (id < 0) {
      throw new ValidationException("Task id must not be negative");
    }
    title = validateTitle(title);
    status = require(status, "status");
    priority = require(priority, "priority");
    createdAt = require(createdAt, "creation time");
    tags = normalizeTags(tags);
    if (status == Status.DONE && completedAt == null) {
      throw new ValidationException("A completed task needs a completion time");
    }
    if (status == Status.OPEN && completedAt != null) {
      throw new ValidationException("An open task must not have a completion time");
    }
  }

  /** Creates a new, not-yet-persisted open task. */
  public static Task create(
      String title,
      Priority priority,
      LocalDate dueDate,
      Collection<String> tags,
      Instant now) {
    return new Task(
        0, title, Status.OPEN, priority, dueDate, tags == null ? Set.of() : new HashSet<>(tags), now, null);
  }

  public Task withId(long newId) {
    return new Task(newId, title, status, priority, dueDate, tags, createdAt, completedAt);
  }

  /** Idempotent: completing a done task returns the same instance. */
  public Task complete(Instant now) {
    if (status == Status.DONE) {
      return this;
    }
    return new Task(id, title, Status.DONE, priority, dueDate, tags, createdAt, now);
  }

  /** Idempotent: reopening an open task returns the same instance. */
  public Task reopen() {
    if (status == Status.OPEN) {
      return this;
    }
    return new Task(id, title, Status.OPEN, priority, dueDate, tags, createdAt, null);
  }

  public boolean isDone() {
    return status == Status.DONE;
  }

  /**
   * Classifies how pressing this task is. Completed tasks and tasks without a due date are {@link
   * Urgency#NONE}.
   *
   * @param soonDays a task due within this many days (but not today) is {@link Urgency#SOON}
   */
  public Urgency urgency(LocalDate today, int soonDays) {
    if (isDone() || dueDate == null) {
      return Urgency.NONE;
    }
    long days = ChronoUnit.DAYS.between(today, dueDate);
    if (days < 0) {
      return Urgency.OVERDUE;
    }
    if (days == 0) {
      return Urgency.TODAY;
    }
    return days <= soonDays ? Urgency.SOON : Urgency.LATER;
  }

  /** Normalises and validates a tag the way tasks do (lower-case, optional leading '#'). */
  public static String normalizeTag(String raw) {
    if (raw == null) {
      throw new ValidationException("Tag must not be null");
    }
    String tag = raw.strip().toLowerCase(Locale.ROOT);
    if (tag.startsWith("#")) {
      tag = tag.substring(1);
    }
    if (!TAG_PATTERN.matcher(tag).matches()) {
      throw new ValidationException(
          "Invalid tag: tags are 1-32 characters of a-z, 0-9, '-' or '_', starting with a letter or digit");
    }
    return tag;
  }

  private static String validateTitle(String raw) {
    if (raw == null) {
      throw new ValidationException("Title is required");
    }
    String title = raw.strip();
    if (title.isEmpty()) {
      throw new ValidationException("Title must not be blank");
    }
    if (title.codePointCount(0, title.length()) > MAX_TITLE_LENGTH) {
      throw new ValidationException("Title must be at most " + MAX_TITLE_LENGTH + " characters");
    }
    if (title.codePoints().anyMatch(Task::isForbiddenCodePoint)) {
      throw new ValidationException(
          "Title must not contain control or bidirectional-override characters");
    }
    return title;
  }

  /**
   * Rejects characters that can corrupt terminal output (ANSI escapes are built from control
   * characters) or visually reorder text ("Trojan Source" style bidi overrides). Because the check
   * lives in the domain, no input path -- CLI, legacy import, hand-edited file -- can bypass it.
   */
  private static boolean isForbiddenCodePoint(int cp) {
    return Character.isISOControl(cp)
        || (cp >= 0x202A && cp <= 0x202E)
        || (cp >= 0x2066 && cp <= 0x2069)
        || cp == 0x200E
        || cp == 0x200F
        || cp == 0x061C;
  }

  private static Set<String> normalizeTags(Set<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return Set.of();
    }
    Set<String> normalized = new TreeSet<>();
    for (String tag : raw) {
      normalized.add(normalizeTag(tag));
    }
    if (normalized.size() > MAX_TAGS) {
      throw new ValidationException("A task can have at most " + MAX_TAGS + " tags");
    }
    return Collections.unmodifiableSet(normalized);
  }

  private static <T> T require(T value, String name) {
    if (value == null) {
      throw new ValidationException("Task " + name + " is required");
    }
    return value;
  }
}
