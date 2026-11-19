package dev.taskflow.infrastructure;

import java.nio.file.Path;
import java.util.Map;

/**
 * Decides where task data lives. Pure function of its inputs (no hidden reads of the environment),
 * so the precedence rules are unit-tested rather than assumed.
 *
 * <p>Precedence: explicit {@code --data-dir} &gt; {@code TASKFLOW_HOME} &gt; {@code
 * $XDG_DATA_HOME/taskflow} &gt; {@code %LOCALAPPDATA%\taskflow} &gt; {@code
 * ~/.local/share/taskflow}.
 *
 * <p>Data deliberately does <em>not</em> default to the current working directory (as the original
 * prototype did): running the tool from a different folder silently showed an empty list.
 */
public final class DataDirResolver {

  private DataDirResolver() {}

  public static Path resolve(Path explicit, Map<String, String> env, Path home) {
    if (explicit != null) {
      return explicit.toAbsolutePath().normalize();
    }
    String taskflowHome = nonBlank(env.get("TASKFLOW_HOME"));
    if (taskflowHome != null) {
      return Path.of(taskflowHome).toAbsolutePath().normalize();
    }
    String xdg = nonBlank(env.get("XDG_DATA_HOME"));
    if (xdg != null) {
      return Path.of(xdg).resolve("taskflow").toAbsolutePath().normalize();
    }
    String localAppData = nonBlank(env.get("LOCALAPPDATA"));
    if (localAppData != null) {
      return Path.of(localAppData).resolve("taskflow").toAbsolutePath().normalize();
    }
    return home.resolve(".local").resolve("share").resolve("taskflow").toAbsolutePath().normalize();
  }

  private static String nonBlank(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
