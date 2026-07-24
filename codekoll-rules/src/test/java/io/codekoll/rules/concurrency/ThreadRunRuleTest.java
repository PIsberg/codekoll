package io.codekoll.rules.concurrency;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ThreadRunRuleTest {

  private final ThreadRunRule rule = new ThreadRunRule();

  @Test
  void flagsRunOnThreadVariable() {
    RuleTestHarness.assertFixture(rule, "P1", """
        class P1 {
          void m(Runnable r) {
            Thread t = new Thread(r);
            t.run(); // :: CK-THREAD-RUN
          }
        }
        """);
  }

  @Test
  void flagsRunOnThreadSubclass() {
    RuleTestHarness.assertFixture(rule, "P2", """
        class P2 {
          static class Worker extends Thread {}
          void m() {
            new Worker().run(); // :: CK-THREAD-RUN
          }
        }
        """);
  }

  @Test
  void allowsStart() {
    RuleTestHarness.assertFixture(rule, "N1", """
        class N1 {
          void m(Runnable r) {
            new Thread(r).start();
          }
        }
        """);
  }

  @Test
  void allowsSuperRunInsideOverride() {
    RuleTestHarness.assertFixture(rule, "N2", """
        class N2 extends Thread {
          @Override
          public void run() {
            super.run();
          }
        }
        """);
  }

  @Test
  void allowsRunOnPlainRunnable() {
    RuleTestHarness.assertFixture(rule, "N3", """
        class N3 {
          void m(Runnable r) {
            r.run();
          }
        }
        """);
  }
}
