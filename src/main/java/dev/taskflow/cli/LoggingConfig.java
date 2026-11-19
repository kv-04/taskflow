package dev.taskflow.cli;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps {@code -v}/{@code -vv} onto log levels. The only place that touches the logback API; all
 * other code logs through SLF4J.
 */
final class LoggingConfig {

  private LoggingConfig() {}

  /**
   * Raises the root log level for the duration of a command.
   *
   * @return an action that restores the previous level (keeps test runs and embedders isolated)
   */
  static Runnable apply(int verbosity) {
    if (verbosity <= 0
        || !(LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
            instanceof ch.qos.logback.classic.Logger root)) {
      return () -> {};
    }
    Level previous = root.getLevel();
    root.setLevel(verbosity == 1 ? Level.INFO : Level.DEBUG);
    return () -> root.setLevel(previous);
  }
}
