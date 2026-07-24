package io.codekoll.rules.correctness;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class IgnoredReturnRuleTest {

  private final IgnoredReturnRule rule = new IgnoredReturnRule();

  @Test
  void flagsDiscardedTrim() {
    RuleTestHarness.assertFixture(rule, "P1", """
        class P1 {
          void m(String name) {
            name.trim(); // :: CK-IGNORED-RETURN
          }
        }
        """);
  }

  @Test
  void flagsDiscardedBigDecimalAdd() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import java.math.BigDecimal;
        class P2 {
          void m(BigDecimal total, BigDecimal amount) {
            total.add(amount); // :: CK-IGNORED-RETURN
          }
        }
        """);
  }

  @Test
  void flagsDiscardedLocalDatePlus() {
    RuleTestHarness.assertFixture(rule, "P3", """
        import java.time.LocalDate;
        class P3 {
          void m(LocalDate date) {
            date.plusDays(1); // :: CK-IGNORED-RETURN
          }
        }
        """);
  }

  @Test
  void allowsAssignedResult() {
    RuleTestHarness.assertFixture(rule, "N1", """
        class N1 {
          String m(String name) {
            name = name.trim();
            return name.toUpperCase(java.util.Locale.ROOT);
          }
        }
        """);
  }

  @Test
  void allowsMutatingReceivers() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import java.util.ArrayList;
        import java.util.HashMap;
        class N2 {
          void m() {
            StringBuilder sb = new StringBuilder();
            sb.append("x");
            new HashMap<String, String>().put("k", "v");
            new ArrayList<String>().remove("x");
          }
        }
        """);
  }

  @Test
  void allowsOptionalOrElseThrow() {
    RuleTestHarness.assertFixture(rule, "N3", """
        import java.util.Optional;
        class N3 {
          void m(Optional<String> value) {
            value.orElseThrow();
          }
        }
        """);
  }
}
