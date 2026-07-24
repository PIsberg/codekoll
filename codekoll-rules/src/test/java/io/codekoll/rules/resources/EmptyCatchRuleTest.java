package io.codekoll.rules.resources;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class EmptyCatchRuleTest {

  private final EmptyCatchRule rule = new EmptyCatchRule();

  @Test
  void flagsEmptyCatch() {
    RuleTestHarness.assertFixture(rule, "P1", """
        class P1 {
          void m() {
            try {
              System.gc();
            } catch (RuntimeException e) { // :: CK-EMPTY-CATCH
            }
          }
        }
        """);
  }

  @Test
  void flagsEmptyCatchAmongMultipleCatches() {
    RuleTestHarness.assertFixture(rule, "P2", """
        class P2 {
          void m() {
            try {
              System.gc();
            } catch (IllegalStateException e) {
              throw new RuntimeException(e);
            } catch (RuntimeException e) { // :: CK-EMPTY-CATCH
            }
          }
        }
        """);
  }

  @Test
  void allowsIgnoredNamingConvention() {
    RuleTestHarness.assertFixture(rule, "N1", """
        class N1 {
          void m() {
            try {
              System.gc();
            } catch (RuntimeException ignored) {
            }
            try {
              System.gc();
            } catch (RuntimeException expected) {
            }
          }
        }
        """);
  }

  @Test
  void allowsNonEmptyCatch() {
    RuleTestHarness.assertFixture(rule, "N2", """
        class N2 {
          void m() {
            try {
              System.gc();
            } catch (RuntimeException e) {
              throw new IllegalStateException(e);
            }
          }
        }
        """);
  }
}
