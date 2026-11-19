package dev.taskflow.cli;

import dev.taskflow.domain.StorageException;
import dev.taskflow.domain.TaskFlowException;
import dev.taskflow.domain.TaskNotFoundException;
import dev.taskflow.domain.ValidationException;

/**
 * Stable, documented process exit codes so scripts can react to failures (see README). Values follow
 * {@code sysexits.h} where an equivalent exists.
 */
final class ExitCodes {
  static final int OK = 0;
  /** Bad command line (reported by picocli). */
  static final int USAGE = 2;

  static final int NOT_FOUND = 3;
  static final int INVALID_INPUT = 4;
  /** EX_SOFTWARE: an unexpected internal error -- a bug. */
  static final int INTERNAL = 70;
  /** EX_IOERR: the data file could not be read or written. */
  static final int IO = 74;

  private ExitCodes() {}

  /** Exhaustive over the sealed hierarchy: a new exception type will not compile until mapped. */
  static int of(TaskFlowException exception) {
    return switch (exception) {
      case ValidationException e -> INVALID_INPUT;
      case TaskNotFoundException e -> NOT_FOUND;
      case StorageException e -> IO;
    };
  }
}
