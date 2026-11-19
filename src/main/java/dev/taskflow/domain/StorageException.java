package dev.taskflow.domain;

/** The persistence layer could not read or write data. Never silently swallowed or "repaired". */
public final class StorageException extends TaskFlowException {
  private static final long serialVersionUID = 1L;

  public StorageException(String message) {
    super(message);
  }

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
