package dev.taskflow.domain;

/** No task exists with the requested id. */
public final class TaskNotFoundException extends TaskFlowException {
  private static final long serialVersionUID = 1L;

  public TaskNotFoundException(long id) {
    super("No task with id " + id);
  }
}
