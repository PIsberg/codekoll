package io.codekoll.rules.pqc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.api.Rule;
import io.codekoll.api.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

/** `--explain` must make the migration actionable (FR-010, FR-011) and the pack must only warn. */
class PqcMetadataTest {

  private static void mentions(String text, String... phrases) {
    for (String phrase : phrases) {
      assertTrue(text.contains(phrase), "expected '" + phrase + "' in: " + text);
    }
  }

  @Test
  void keyExchangeExplainsUrgencyAndTheHybridMigration() {
    Rule rule = new PqcKeyExchangeRule();
    mentions(rule.explanation(), "Shor", "recorded", "not a drop-in", "never rewrites");
    mentions(rule.fix(), "ML-KEM", "hybrid", "Java 24", "Bouncy Castle");
  }

  @Test
  void signatureExplainsForgeryAndTheReplacement() {
    Rule rule = new PqcSignatureRule();
    mentions(rule.explanation(), "Shor", "forge", "not a drop-in", "never rewrites");
    mentions(rule.fix(), "ML-DSA", "Java 24", "Bouncy Castle");
  }

  @Test
  void keyMaterialExplainsBothRoles() {
    Rule rule = new PqcKeyMaterialRule();
    mentions(rule.explanation(), "Shor", "recorded", "forged", "not a drop-in", "never rewrites");
    mentions(rule.fix(), "ML-KEM", "ML-DSA", "hybrid", "Java 24", "Bouncy Castle");
  }

  @Test
  void noRuleInThePackDefaultsToError() {
    for (Rule rule : List.of(new PqcKeyExchangeRule(), new PqcSignatureRule(), new PqcKeyMaterialRule())) {
      assertNotEquals(Severity.ERROR, rule.defaultSeverity(), rule.id().value());
    }
  }
}
