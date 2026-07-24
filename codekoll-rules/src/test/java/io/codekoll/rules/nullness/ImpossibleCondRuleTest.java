package io.codekoll.rules.nullness;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ImpossibleCondRuleTest {

  private final ImpossibleCondRule rule = new ImpossibleCondRule();

  @Test
  void flagsNullThenDereferenceInAnd() {
    RuleTestHarness.assertFixture(rule, "P1", """
        class P1 {
          void m(String id) {
            if (id == null && id.length() > 5) { // :: CK-IMPOSSIBLE-COND
              System.out.println("valid");
            }
          }
        }
        """);
  }

  @Test
  void flagsDirectContradiction() {
    RuleTestHarness.assertFixture(rule, "P2", """
        class P2 {
          boolean m(String x) {
            return x == null && x != null; // :: CK-IMPOSSIBLE-COND
          }
        }
        """);
  }

  @Test
  void flagsNonNullGuardThenDereferenceInOr() {
    RuleTestHarness.assertFixture(rule, "P3", """
        class P3 {
          boolean m(String x) {
            return x != null || x.length() > 5; // :: CK-IMPOSSIBLE-COND
          }
        }
        """);
  }

  @Test
  void flagsDereferenceThenNullAssertInAnd() {
    RuleTestHarness.assertFixture(rule, "P4", """
        class P4 {
          boolean m(int[] a) {
            return a.length > 0 && a == null; // :: CK-IMPOSSIBLE-COND
          }
        }
        """);
  }

  @Test
  void allowsCorrectGuardThenUse() {
    RuleTestHarness.assertFixture(rule, "N1", """
        class N1 {
          boolean m(String id) {
            return id != null && id.length() > 5;
          }
        }
        """);
  }

  @Test
  void allowsCorrectOrIdiom() {
    RuleTestHarness.assertFixture(rule, "N2", """
        class N2 {
          boolean m(String s) {
            return s == null || s.isEmpty();
          }
        }
        """);
  }

  @Test
  void allowsUnrelatedVariables() {
    RuleTestHarness.assertFixture(rule, "N3", """
        class N3 {
          boolean m(String a, String b) {
            return a == null && b.length() > 5;
          }
        }
        """);
  }

  @Test
  void methodCallInvalidatesFactsConservatively() {
    RuleTestHarness.assertFixture(rule, "N4", """
        class N4 {
          private String x;
          private boolean refresh() {
            x = "loaded";
            return true;
          }
          boolean m() {
            return x == null && refresh() && x.length() > 0;
          }
        }
        """);
  }
}
