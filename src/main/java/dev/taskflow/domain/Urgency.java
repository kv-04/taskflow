package dev.taskflow.domain;

/** How pressing a task is, relative to a given "today". */
public enum Urgency {
  OVERDUE,
  TODAY,
  SOON,
  LATER,
  NONE;

  /** Whether a reminder should be raised for this level. */
  public boolean isActionable() {
    return this == OVERDUE || this == TODAY || this == SOON;
  }
}
