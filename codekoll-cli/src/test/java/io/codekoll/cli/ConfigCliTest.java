package io.codekoll.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * {@code codekoll.toml} end to end: what it changes about a run, and what it is not allowed to
 * change (CLI-SPEC §10 and §14).
 *
 * <p>Each test analyzes a one-file repository whose single class carries a known weak-crypto
 * finding, so the assertion is about the setting rather than about which rules exist.
 */
class ConfigCliTest {

  @TempDir
  Path repo;

  private record Run(int exitCode, String output) {}

  @BeforeEach
  void writeRepository() throws IOException {
    Path source = repo.resolve("src/main/java/app/Weak.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, """
        package app;

        import java.security.MessageDigest;

        public class Weak {
          public void hash() throws Exception {
            MessageDigest.getInstance("MD5");
          }
        }
        """, StandardCharsets.UTF_8);
    Files.writeString(repo.resolve("pom.xml"), """
        <project>
          <groupId>t</groupId><artifactId>cfg</artifactId><version>1</version>
          <properties><maven.compiler.release>21</maven.compiler.release></properties>
        </project>
        """, StandardCharsets.UTF_8);
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

  // ------------------------------------------------------- what it changes

  @Test
  void withNoConfigTheWeakCryptoFindingIsReportedAndFailsTheRun() throws IOException {
    Run run = run();

    assertEquals(1, run.exitCode(), run.output());
    assertTrue(run.output().contains("CK-CRYPTO-WEAK"), run.output());
  }

  @Test
  void disablingARuleInTheConfigSilencesIt() throws IOException {
    config("""
        [rules]
        disable = ["CK-CRYPTO-WEAK"]
        """);

    Run run = run();

    assertEquals(0, run.exitCode(), run.output());
    assertFalse(run.output().contains("CK-CRYPTO-WEAK"), run.output());
  }

  @Test
  void disablingAPackSilencesEveryRuleInIt() throws IOException {
    config("""
        [rules]
        disable-packs = ["security"]
        """);

    assertFalse(run().output().contains("CK-CRYPTO-WEAK"));
  }

  @Test
  void enableOnlyIsAnAllowlist() throws IOException {
    config("""
        [rules]
        enable-only = ["CK-EMPTY-CATCH"]
        """);

    Run run = run();

    assertEquals(0, run.exitCode(), run.output());
    assertFalse(run.output().contains("CK-CRYPTO-WEAK"), run.output());
  }

  /**
   * The load-bearing case for severity overrides: lowering a rule has to change the exit code too,
   * or the setting is obeyed in the printout and ignored where it matters.
   */
  @Test
  void aSeverityOverrideChangesBothTheReportAndTheExitCode() throws IOException {
    config("""
        [severity]
        "CK-CRYPTO-WEAK" = "info"
        """);

    Run run = run();

    assertEquals(0, run.exitCode(), "INFO does not fail at --fail-on error: " + run.output());
    assertTrue(run.output().contains("CK-CRYPTO-WEAK"), run.output());
    assertTrue(run.output().contains("INFO"), run.output());
  }

  @Test
  void suppressPathsDropsTheFileEntirely() throws IOException {
    config("""
        [suppress]
        paths = ["**/app/**"]
        """);

    assertFalse(run().output().contains("CK-CRYPTO-WEAK"));
  }

  @Test
  void reportSettingsAreHonoured() throws IOException {
    config("""
        [report]
        format = "json"
        fail-on = "never"
        """);

    Run run = run();

    assertEquals(0, run.exitCode(), run.output());
    assertTrue(run.output().startsWith("["), run.output());
  }

  @Test
  void aCommandLineFlagBeatsTheConfigFile() throws IOException {
    config("""
        [report]
        format = "json"
        """);

    assertFalse(run("--format", "console").output().startsWith("["));
  }

  @Test
  void anExplicitConfigReplacesTheRepositorySettings() throws IOException {
    config("""
        [rules]
        disable = ["CK-CRYPTO-WEAK"]
        """);
    Path mine = repo.resolve("mine.toml");
    Files.writeString(mine, "[report]\nfail-on = \"never\"\n");

    Run run = run("--config", mine.toString());

    assertEquals(0, run.exitCode());
    assertTrue(run.output().contains("CK-CRYPTO-WEAK"),
        "the repo's disable list is not consulted: " + run.output());
  }

  // ------------------------------------------------------------ visibility

  @Test
  void printConfigShowsEveryValueAndWhereItCameFrom() throws IOException {
    config("""
        [rules]
        disable = ["CK-CRYPTO-WEAK"]

        [report]
        fail-on = "warning"
        """);

    Run run = run("--print-config");

    assertEquals(0, run.exitCode());
    assertTrue(run.output().contains("rules.disable"), run.output());
    assertTrue(run.output().contains("[CK-CRYPTO-WEAK]"), run.output());
    assertTrue(run.output().contains("codekoll.toml:2"), run.output());
    assertTrue(run.output().contains("codekoll.toml:5"), run.output());
  }

  @Test
  void printConfigSaysSoWhenThereIsNoConfiguration() throws IOException {
    assertTrue(run("--print-config").output().contains("No configuration file in effect"));
  }

  // ------------------------------------------ rejected rather than ignored

  @Test
  void anUnknownRuleIdInTheConfigIsAnError() throws IOException {
    config("""
        [rules]
        disable = ["CK-NO-SUCH-RULE"]
        """);

    Run run = run();

    assertEquals(2, run.exitCode());
    assertTrue(run.output().contains("unknown rule id 'CK-NO-SUCH-RULE'"), run.output());
  }

  @Test
  void anUnknownRuleIdInTheSeverityTableIsAnError() throws IOException {
    config("""
        [severity]
        "CK-NO-SUCH-RULE" = "error"
        """);

    Run run = run();

    assertEquals(2, run.exitCode());
    assertTrue(run.output().contains("unknown rule id"), run.output());
  }

  @Test
  void aSeverityThatIsNotASeverityIsAnError() throws IOException {
    config("""
        [severity]
        "CK-CRYPTO-WEAK" = "critical"
        """);

    Run run = run();

    assertEquals(2, run.exitCode());
    assertTrue(run.output().contains("expected error, warning or info"), run.output());
  }

  @Test
  void anUnknownKeyIsAnErrorRatherThanASilentNoOp() throws IOException {
    config("""
        [rules]
        disabled = ["CK-CRYPTO-WEAK"]
        """);

    Run run = run();

    assertEquals(2, run.exitCode());
    assertTrue(run.output().contains("unknown key 'rules.disabled'"), run.output());
  }

  /** §14: the repository may describe itself, never the machine analyzing it. */
  @Test
  void aRepositoryCannotTalkCodekollIntoRunningItsBuild() throws IOException {
    config("""
        [resolve]
        mode = "build"
        """);

    Run run = run();

    assertEquals(2, run.exitCode());
    assertTrue(run.output().contains("may not be set by the analyzed repository"), run.output());
    assertTrue(run.output().contains("resolve.mode"), run.output());
  }

  @Test
  void aRepositoryCannotLoadRuleCodeItControls() throws IOException {
    config("""
        [rules]
        rule-path = ["evil.jar"]
        """);

    Run run = run();

    assertEquals(2, run.exitCode());
    assertTrue(run.output().contains("rules.rule-path"), run.output());
  }

  @Test
  void aRepositoryCannotRedirectOutputOutsideItself() throws IOException {
    config("""
        [report]
        output = "../escaped.sarif"
        """);

    Run run = run();

    assertEquals(2, run.exitCode());
    assertTrue(run.output().contains("outside the repository"), run.output());
  }
}
