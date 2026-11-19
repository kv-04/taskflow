package dev.taskflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataDirResolverTest {

  private static final Path HOME = Path.of("/home/alice");

  /** Expected paths are made absolute the same way production code does, so this also passes on Windows. */
  private static Path abs(String path) {
    return Path.of(path).toAbsolutePath().normalize();
  }

  @Test
  void explicitOptionWinsOverEverything() {
    Map<String, String> env = Map.of("TASKFLOW_HOME", "/env", "XDG_DATA_HOME", "/xdg");
    assertThat(DataDirResolver.resolve(Path.of("/explicit"), env, HOME))
        .isEqualTo(abs("/explicit"));
  }

  @Test
  void taskflowHomeBeatsXdg() {
    Map<String, String> env = Map.of("TASKFLOW_HOME", "/env", "XDG_DATA_HOME", "/xdg");
    assertThat(DataDirResolver.resolve(null, env, HOME)).isEqualTo(abs("/env"));
  }

  @Test
  void xdgDataHomeGetsATaskflowSubdirectory() {
    assertThat(DataDirResolver.resolve(null, Map.of("XDG_DATA_HOME", "/xdg"), HOME))
        .isEqualTo(abs("/xdg/taskflow"));
  }

  @Test
  void blankVariablesAreIgnored() {
    assertThat(DataDirResolver.resolve(null, Map.of("TASKFLOW_HOME", "  ", "XDG_DATA_HOME", ""), HOME))
        .isEqualTo(abs("/home/alice/.local/share/taskflow"));
  }

  @Test
  void fallsBackToTheUserHomeNeverTheWorkingDirectory() {
    assertThat(DataDirResolver.resolve(null, Map.of(), HOME))
        .isEqualTo(abs("/home/alice/.local/share/taskflow"));
  }

  @Test
  void normalisesTraversalSegments() {
    assertThat(DataDirResolver.resolve(Path.of("/data/../data/tasks"), Map.of(), HOME))
        .isEqualTo(abs("/data/tasks"));
  }
}
