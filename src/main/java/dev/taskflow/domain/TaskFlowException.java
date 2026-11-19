package dev.taskflow.domain;

/**
 * Root of all expected, user-facing failures.
 *
 * <p>The hierarchy is {@code sealed} so the CLI can map every failure to an exit code with an
 * exhaustive {@code switch}; adding a new failure type is a compile error until it is handled.
 * Anything that is <em>not</em> a {@code TaskFlowException} is a bug.
 */
public abstract sealed class TaskFlowException extends RuntimeException
    permits ValidationException, TaskNotFoundException, StorageException {
  private static final long serialVersionUID = 1L;


  protected TaskFlowException(String message) {
    super(message);
  }

  protected TaskFlowException(String message, Throwable cause) {
    super(message, cause);
  }
}
