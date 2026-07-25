package io.codekoll.rules;

import io.codekoll.engine.testing.RuleTestHarness;
import io.codekoll.rules.concurrency.CtorThreadStartRule;
import io.codekoll.rules.numeric.FloatEqualityRule;
import io.codekoll.rules.resources.CatchNpeRule;
import io.codekoll.rules.resources.LostCauseRule;
import org.junit.jupiter.api.Test;

class WaveAStragglersTest {

  @Test
  void floatEqualityFlagged() {
    RuleTestHarness.assertFixture(new FloatEqualityRule(), "P1", """
        class P1 {
          boolean m(double a, double b) {
            return a == b; // :: CK-FLOAT-EQUALITY
          }
        }
        """);
  }

  @Test
  void zeroComparisonAndToleranceAllowed() {
    RuleTestHarness.assertFixture(new FloatEqualityRule(), "N1", """
        class N1 {
          boolean m(double a, double b) {
            return a == 0.0 || Math.abs(a - b) < 1e-9;
          }
        }
        """);
  }

  @Test
  void ctorThreadStartFlagged() {
    RuleTestHarness.assertFixture(new CtorThreadStartRule(), "P2", """
        class P2 {
          private final Thread worker;
          P2(Runnable task) {
            worker = new Thread(task);
            worker.start(); // :: CK-CTOR-THREAD-START
          }
        }
        """);
  }

  @Test
  void startFromMethodAllowed() {
    RuleTestHarness.assertFixture(new CtorThreadStartRule(), "N2", """
        class N2 {
          private final Thread worker;
          N2(Runnable task) {
            worker = new Thread(task);
          }
          void start() {
            worker.start();
          }
        }
        """);
  }

  @Test
  void lostCauseFlagged() {
    RuleTestHarness.assertFixture(new LostCauseRule(), "P3", """
        class P3 {
          void m() {
            try {
              System.gc();
            } catch (RuntimeException e) {
              throw new IllegalStateException("operation failed"); // :: CK-LOST-CAUSE
            }
          }
        }
        """);
  }

  @Test
  void causePassedOrVariableUsedAllowed() {
    RuleTestHarness.assertFixture(new LostCauseRule(), "N3", """
        class N3 {
          void withCause() {
            try {
              System.gc();
            } catch (RuntimeException e) {
              throw new IllegalStateException("failed", e);
            }
          }
          void logged() {
            try {
              System.gc();
            } catch (RuntimeException e) {
              System.out.println("failed: " + e.getMessage());
              throw new IllegalStateException("failed after logging");
            }
          }
        }
        """);
  }

  @Test
  void catchNpeFlagged() {
    RuleTestHarness.assertFixture(new CatchNpeRule(), "P4", """
        class P4 {
          String m(String s) {
            try {
              return s.trim();
            } catch (NullPointerException e) { // :: CK-CATCH-NPE
              return "";
            }
          }
        }
        """);
  }

  @Test
  void specificCatchesAllowed() {
    RuleTestHarness.assertFixture(new CatchNpeRule(), "N4", """
        class N4 {
          String m(String s) {
            try {
              return s == null ? "" : s.trim();
            } catch (IllegalStateException e) {
              return "";
            }
          }
        }
        """);
  }
}
