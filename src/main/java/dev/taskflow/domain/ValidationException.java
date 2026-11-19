package dev.taskflow.domain;

/** Input (or persisted data) violates a domain rule. */
public final class ValidationException extends TaskFlowException {
  private static final long serialVersionUID = 1L;

  public ValidationException(String message) {
    super(message);
  }
}
