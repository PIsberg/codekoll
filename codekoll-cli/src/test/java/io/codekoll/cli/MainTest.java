package io.codekoll.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class MainTest {

  @TempDir
  Path dir;

  private record Run(int exitCode, String out) {}

  private Run run(String... args) {
    StringWriter sw = new StringWriter();
    CommandLine cmd = new CommandLine(new Main());
    cmd.setOut(new PrintWriter(sw, true));
    cmd.setErr(new PrintWriter(sw, true));
    int code = cmd.execute(args);
    return new Run(code, sw.toString());
  }

  @Test
  void findsBugAndExitsNonZero() throws Exception {
    Path file = dir.resolve("Bad.java");
    Files.writeString(file, """
        import java.security.MessageDigest;
        class Bad {
          void m() throws Exception {
            MessageDigest.getInstance("MD5");
          }
        }
        """);
    // Main prints to System.out via its own writer; findings decide the exit code.
    Run run = run("--fail-on", "error", file.toString());
    assertEquals(1, run.exitCode());
  }

  @Test
  void cleanSourceExitsZero() throws Exception {
    Path file = dir.resolve("Good.java");
    Files.writeString(file, """
        class Good {
          int add(int a, int b) {
            return a + b;
          }
        }
        """);
    Run run = run(file.toString());
    assertEquals(0, run.exitCode());
  }

  @Test
  void failOnNeverAlwaysExitsZero() throws Exception {
    Path file = dir.resolve("Bad2.java");
    Files.writeString(file, """
        class Bad2 {
          void m(Runnable r) {
            new Thread(r).run();
          }
        }
        """);
    Run run = run("--fail-on", "never", file.toString());
    assertEquals(0, run.exitCode());
  }

  @Test
  void packsFilterRestrictsRules() throws Exception {
    Path file = dir.resolve("Bad3.java");
    Files.writeString(file, """
        class Bad3 {
          void m(Runnable r) {
            new Thread(r).run();
          }
        }
        """);
    // Thread.run bug is in the concurrency pack; restricting to security must not flag it.
    Run run = run("--packs", "security", "--fail-on", "error", file.toString());
    assertEquals(0, run.exitCode());
  }

  @Test
  void explainPrintsRuleMetadata() {
    Run run = run("--explain", "CK-THREAD-RUN", "unused-path-arg");
    assertEquals(0, run.exitCode());
  }

  @Test
  void unknownExplainIdFails() {
    Run run = run("--explain", "CK-DOES-NOT-EXIST", "unused-path-arg");
    assertEquals(2, run.exitCode());
  }

  @Test
  void helpMentionsAllOptions() {
    Run run = run("--help");
    assertEquals(0, run.exitCode());
    for (String option : new String[] {"--classpath", "--release", "--format", "--fail-on",
        "--rules", "--packs", "--explain"}) {
      assertTrue(run.out().contains(option), "help must document " + option);
    }
  }
}
