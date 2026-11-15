package io.codekoll.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * The dead-code pack end to end: output formats, configuration, suppression and {@code --explain}
 * (010 FR-005, FR-006, SC-004).
 *
 * <p>The repository holds one file per rule, each with exactly one finding, so a finding is
 * attributed to its file name.
 */
class DeadCodeCliTest {

  private static final List<String> IDS =
      List.of("CK-DEAD-BRANCH", "CK-UNUSED-LOCAL", "CK-UNREACHABLE", "CK-UNUSED-PRIVATE");

  @TempDir
  Path repo;

  private record Run(int exitCode, String output) {}

  @BeforeEach
  void writeRepository() throws IOException {
    source("DeadBranchCase", """
        package app;

        public class DeadBranchCase {
          public int m(int a) {
            if (false) {
              a++;
            }
            return a;
          }
        }
        """);
    source("UnusedLocalCase", """
        package app;

        public class UnusedLocalCase {
          public void m() {
            int leftover = 5;
          }
        }
        """);
    source("UnreachableCase", """
        package app;

        public class UnreachableCase {
          public int m() {
            if (true) {
              return 1;
            }
            return 2;
          }
        }
        """);
    source("UnusedPrivateCase", """
        package app;

        public class UnusedPrivateCase {
          private void helper() {}
        }
        """);
    Files.writeString(repo.resolve("pom.xml"), """
        <project>
          <groupId>t</groupId><artifactId>dc</artifactId><version>1</version>
          <properties><maven.compiler.release>21</maven.compiler.release></properties>
        </project>
        """, StandardCharsets.UTF_8);
  }

  private void source(String name, String text) throws IOException {
    Path file = repo.resolve("src/main/java/app/" + name + ".java");
    Files.createDirectories(file.getParent());
    Files.writeString(file, text, StandardCharsets.UTF_8);
  }

  private void config(String toml) throws IOException {
    Files.writeString(repo.resolve("codekoll.toml"), toml, StandardCharsets.UTF_8);
  }

  private Run run(String... args) throws IOException {
    Path file = repo.resolve("out-" + System.nanoTime() + ".txt");
    String[] all = new String[args.length + 5];
    System.arraycopy(args, 0, all, 0, args.length);
    all[args.length] = "--repo";
    all[args.length + 1] = repo.toString();
    all[args.length + 2] = "--output";
    all[args.length + 3] = file.toString();
    all[args.length + 4] = repo.toString();
    int code = new CommandLine(new Main()).execute(all);
    return new Run(code, Files.exists(file) ? Files.readString(file) : "");
  }

  private Run explain(String id) {
    StringWriter sw = new StringWriter();
    CommandLine cmd = new CommandLine(new Main());
    cmd.setOut(new PrintWriter(sw, true));
    cmd.setErr(new PrintWriter(sw, true));
    int code = cmd.execute("--explain", id);
    return new Run(code, sw.toString());
  }

  private static void assertAllRulesAndFiles(String output) {
    for (String id : IDS) {
      assertTrue(output.contains(id), id + " missing: " + output);
    }
    for (String file : List.of("DeadBranchCase.java", "UnusedLocalCase.java",
        "UnreachableCase.java", "UnusedPrivateCase.java")) {
      assertTrue(output.contains(file), file + " missing: " + output);
    }
  }

  @Test
  void consoleShowsEveryRuleWithFileAndLine() throws IOException { // 010 FR-006 US3-scenario-1
    Run run = run("--format", "console");

    assertAllRulesAndFiles(run.output());
    assertTrue(run.output().contains("DeadBranchCase.java:5"), run.output());
    assertTrue(run.output().contains("UnusedLocalCase.java:5"), run.output());
    assertTrue(run.output().contains("UnreachableCase.java:8"), run.output());
    assertTrue(run.output().contains("UnusedPrivateCase.java:4"), run.output());
  }

  @Test
  void jsonShowsEveryRuleWithFile() throws IOException { // 010 FR-005 FR-006 US3-scenario-1
    Run run = run("--format", "json");

    assertTrue(run.output().startsWith("["), run.output());
    assertAllRulesAndFiles(run.output());
  }

  @Test
  void sarifShowsEveryRuleWithFile() throws IOException { // 010 FR-005 FR-006 US3-scenario-1
    Run run = run("--format", "sarif");

    assertAllRulesAndFiles(run.output());
    assertTrue(run.output().contains("\"startLine\""), run.output());
  }

  @Test
  void disablingThePackSilencesAllFour() throws IOException { // 010 FR-006 US3-scenario-3
    config("""
        [rules]
        disable-packs = ["dead-code"]
        """);

    Run run = run();

    assertEquals(0, run.exitCode(), run.output());
    for (String id : IDS) {
      assertFalse(run.output().contains(id), id + " still reported: " + run.output());
    }
  }

  @Test
  void aSeverityOverrideToErrorChangesTheExitCode() throws IOException { // 010 FR-006
    assertEquals(0, run().exitCode(), "dead-code defaults never fail at --fail-on error");
    config("""
        [severity]
        "CK-UNUSED-LOCAL" = "error"
        """);

    Run run = run();

    assertEquals(1, run.exitCode(), run.output());
    assertTrue(run.output().contains("CK-UNUSED-LOCAL"), run.output());
  }

  @Test
  void suppressionCommentDropsOneFinding() throws IOException { // 010 FR-006
    source("UnusedLocalCase", """
        package app;

        public class UnusedLocalCase {
          public void m() {
            int leftover = 5; // codekoll:off CK-UNUSED-LOCAL
          }
        }
        """);

    Run run = run();

    assertFalse(run.output().contains("CK-UNUSED-LOCAL"), run.output());
    assertTrue(run.output().contains("CK-DEAD-BRANCH"), run.output());
    assertTrue(run.output().contains("CK-UNREACHABLE"), run.output());
    assertTrue(run.output().contains("CK-UNUSED-PRIVATE"), run.output());
  }

  @Test
  void explainPrintsExplanationAndFixForEachId() { // 010 FR-005 SC-004 US3-scenario-2
    var rules = new io.codekoll.rules.deadcode.DeadBranchRule();
    List<io.codekoll.api.Rule> all = List.of(
        rules,
        new io.codekoll.rules.deadcode.UnusedLocalRule(),
        new io.codekoll.rules.deadcode.UnreachableRule(),
        new io.codekoll.rules.deadcode.UnusedPrivateRule());
    for (io.codekoll.api.Rule rule : all) {
      Run run = explain(rule.id().value());

      assertEquals(0, run.exitCode(), run.output());
      assertTrue(run.output().contains(rule.explanation()), run.output());
      assertTrue(run.output().contains(rule.fix()), run.output());
    }
  }
}
