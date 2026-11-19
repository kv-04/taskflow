package dev.taskflow.application;

import dev.taskflow.domain.Task;

/**
 * Filter for listing tasks.
 *
 * @param tag optional tag filter (normalised on construction), {@code null} for any
 */
public record TaskQuery(StatusFilter status, String tag, boolean overdueOnly) {

  public enum StatusFilter {
    OPEN,
    DONE,
    ALL
  }

  public TaskQuery {
    status = status == null ? StatusFilter.OPEN : status;
    tag = tag == null ? null : Task.normalizeTag(tag);
  }

  public static TaskQuery openTasks() {
    return new TaskQuery(StatusFilter.OPEN, null, false);
  }
}
