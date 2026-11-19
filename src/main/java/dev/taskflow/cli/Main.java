package dev.taskflow.cli;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;

/** Process entry point. Deliberately trivial: all logic lives in {@link TaskFlowCli}. */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
    PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);

    int exitCode =
        TaskFlowCli.create(
                Clock.systemDefaultZone(),
                System.getenv(),
                Path.of(System.getProperty("user.home")),
                out,
                err)
            .execute(args);

    out.flush();
    err.flush();
    System.exit(exitCode);
  }
}
