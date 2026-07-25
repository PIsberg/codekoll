package io.codekoll.rules.resources;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ResourcesRulesTest {

  @Test
  void printStackTraceFlagged() {
    RuleTestHarness.assertFixture(new PrintStackTraceRule(), "P1", """
        class P1 {
          void m() {
            try {
              System.gc();
            } catch (RuntimeException e) {
              e.printStackTrace(); // :: CK-PRINT-STACKTRACE
            }
          }
        }
        """);
  }

  @Test
  void loggerStyleHandlingAllowed() {
    RuleTestHarness.assertFixture(new PrintStackTraceRule(), "N1", """
        class N1 {
          void m() {
            try {
              System.gc();
            } catch (RuntimeException e) {
              throw new IllegalStateException("failed", e);
            }
          }
        }
        """);
  }

  @Test
  void finalizeOverrideFlagged() {
    RuleTestHarness.assertFixture(new FinalizeRule(), "P2", """
        class P2 {
          @SuppressWarnings({"deprecation", "removal"}) // :: CK-FINALIZE
          protected void finalize() {
            System.gc();
          }
        }
        """);
  }

  @Test
  void unrelatedFinalizeOverloadAllowed() {
    RuleTestHarness.assertFixture(new FinalizeRule(), "N2", """
        class N2 implements AutoCloseable {
          void finalize(String report) {
            System.out.println(report);
          }
          @Override
          public void close() {
          }
        }
        """);
  }

  @Test
  void throwAndReturnInFinallyFlagged() {
    RuleTestHarness.assertFixture(new ThrowInFinallyRule(), "P3", """
        class P3 {
          int m() {
            try {
              return compute();
            } finally {
              return -1; // :: CK-THROW-IN-FINALLY
            }
          }
          void n() {
            try {
              compute();
            } finally {
              throw new IllegalStateException("cleanup"); // :: CK-THROW-IN-FINALLY
            }
          }
          int compute() {
            return 42;
          }
        }
        """);
  }

  @Test
  void cleanupOnlyFinallyAllowed() {
    RuleTestHarness.assertFixture(new ThrowInFinallyRule(), "N3", """
        class N3 {
          int m() {
            try {
              return 1;
            } finally {
              System.gc();
            }
          }
          Runnable lambdaInFinally() {
            try {
              return () -> {};
            } finally {
              Runnable r = () -> {
                return;
              };
              r.run();
            }
          }
        }
        """);
  }

  @Test
  void catchThrowableFlagged() {
    RuleTestHarness.assertFixture(new CatchBroadRule(), "P4", """
        class P4 {
          void m() {
            try {
              System.gc();
            } catch (Throwable t) { // :: CK-CATCH-BROAD
              System.out.println(t.getMessage());
            }
          }
        }
        """);
  }

  @Test
  void rethrowAndFrameworkTopLevelAllowed() {
    RuleTestHarness.assertFixture(new CatchBroadRule(), "N4", """
        class N4 {
          void rethrows() {
            try {
              System.gc();
            } catch (Throwable t) {
              throw t;
            }
          }
          static class TaskRunner {
            void run() {
              try {
                System.gc();
              } catch (Throwable t) {
                System.out.println("top-level: " + t);
              }
            }
          }
          void narrow() {
            try {
              System.gc();
            } catch (Exception e) {
              System.out.println(e.getMessage());
            }
          }
        }
        """);
  }
}
