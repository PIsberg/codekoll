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
 * The pqc pack is a guardrail that warns: on by default, never failing a default run, failing only
 * when a team opts in through the threshold or a severity override.
 */
class PqcGuardrailCliTest {

  private static final String KEYS = """
      package app;

      import java.security.Signature;
      import javax.crypto.KeyAgreement;

      public class Keys {
        public void use() throws Exception {
          Signature.getInstance("SHA256withECDSA");
          KeyAgreement.getInstance("ECDH");
        }
      }
      """;

  @TempDir
  Path repo;

  private record Run(int exitCode, String output) {}

  @BeforeEach
  void writeRepository() throws IOException {
    source("src/main/java/app/Keys.java", KEYS);
    Files.writeString(repo.resolve("pom.xml"), """
        <project>
          <groupId>t</groupId><artifactId>pqc</artifactId><version>1</version>
          <properties><maven.compiler.release>25</maven.compiler.release></properties>
        </project>
        """, StandardCharsets.UTF_8);
  }

  private void source(String relative, String content) throws IOException {
    Path file = repo.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
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

  @Test
  void enabledByDefaultAndNeverFailsADefaultRun() throws IOException {
    Run run = run();

    assertEquals(0, run.exitCode(), run.output());
    assertTrue(run.output().contains("CK-PQC-SIGNATURE"), run.output());
    assertTrue(run.output().contains("CK-PQC-KEY-EXCHANGE"), run.output());
  }

  @Test
  void failsWhenTheTeamRaisesTheThresholdToWarning() throws IOException {
    assertEquals(1, run("--fail-on", "warning").exitCode());
  }

  @Test
  void keyMaterialIsInfoAndDoesNotFailEvenAtWarning() throws IOException {
    source("src/main/java/app/Keys.java", """
        package app;

        import java.security.KeyPairGenerator;

        public class Keys {
          public void use() throws Exception {
            KeyPairGenerator.getInstance("EC");
          }
        }
        """);

    Run run = run("--fail-on", "warning");

    assertEquals(0, run.exitCode(), run.output());
    assertTrue(run.output().contains("CK-PQC-KEY-MATERIAL"), run.output());
  }

  @Test
  void aSeverityOverrideTurnsTheGuardrailIntoAGate() throws IOException {
    Files.writeString(repo.resolve("codekoll.toml"), """
        [severity]
        "CK-PQC-SIGNATURE" = "error"
        """, StandardCharsets.UTF_8);

    assertEquals(1, run().exitCode());
  }

  @Test
  void aLineCommentSuppressesOneSiteOnly() throws IOException {
    source("src/main/java/app/Keys.java", KEYS.replace("Signature.getInstance(\"SHA256withECDSA\");",
        "Signature.getInstance(\"SHA256withECDSA\"); // codekoll:off CK-PQC-SIGNATURE"));

    Run run = run();

    assertFalse(run.output().contains("CK-PQC-SIGNATURE"), run.output());
    assertTrue(run.output().contains("CK-PQC-KEY-EXCHANGE"), run.output());
  }

  @Test
  void packSelectionAndDisablingWorkByPackName() throws IOException {
    assertTrue(run("--packs", "pqc").output().contains("CK-PQC-KEY-EXCHANGE"));
    assertFalse(run("--packs", "security").output().contains("CK-PQC-"));

    Files.writeString(repo.resolve("codekoll.toml"), """
        [rules]
        disable-packs = ["pqc"]
        """, StandardCharsets.UTF_8);
    assertFalse(run().output().contains("CK-PQC-"));
  }

  @Test
  void machineReadableOutputsCarryTheRuleIds() throws IOException {
    String json = run("--format", "json").output();
    assertTrue(json.contains("CK-PQC-SIGNATURE") && json.contains("CK-PQC-KEY-EXCHANGE"), json);

    String sarif = run("--format", "sarif").output();
    assertTrue(sarif.contains("\"ruleId\": \"CK-PQC-SIGNATURE\""), sarif);
    assertTrue(sarif.contains("\"ruleId\": \"CK-PQC-KEY-EXCHANGE\""), sarif);
  }

  @Test
  void testSourcesAreNotReported() throws IOException {
    Files.delete(repo.resolve("src/main/java/app/Keys.java"));
    source("src/test/java/app/KeysTest.java", KEYS.replace("class Keys", "class KeysTest"));

    Run run = run();

    assertFalse(run.output().contains("CK-PQC-"), run.output());
  }
}
