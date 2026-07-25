package io.codekoll.rules;

import io.codekoll.engine.testing.RuleTestHarness;
import io.codekoll.rules.concurrency.WaitNoLoopRule;
import io.codekoll.rules.numeric.IntOverflowWidenRule;
import io.codekoll.rules.numeric.OctalLiteralRule;
import io.codekoll.rules.performance.ContainsInLoopRule;
import org.junit.jupiter.api.Test;

class SmallRulesBatchTest {

  @Test
  void octalLiteralFlagged() {
    RuleTestHarness.assertFixture(new OctalLiteralRule(), "P1", """
        class P1 {
          int m() {
            return 0100; // :: CK-OCTAL-LITERAL
          }
        }
        """);
  }

  @Test
  void decimalAndHexAllowed() {
    RuleTestHarness.assertFixture(new OctalLiteralRule(), "N1", """
        class N1 {
          int[] m() {
            return new int[] {100, 0x1F, 0b1010, 0};
          }
        }
        """);
  }

  @Test
  void intOverflowWidenFlagged() {
    RuleTestHarness.assertFixture(new IntOverflowWidenRule(), "P2", """
        class P2 {
          long m(int days) {
            long ms = days * 86_400_000; // :: CK-INT-OVERFLOW-WIDEN
            return ms;
          }
        }
        """);
  }

  @Test
  void longOperandAllowed() {
    RuleTestHarness.assertFixture(new IntOverflowWidenRule(), "N2", """
        class N2 {
          long m(int days, long factor) {
            long ms = days * 86_400_000L;
            long widened = (long) days * 2;
            long allLong = factor * 100;
            return ms + widened + allLong;
          }
        }
        """);
  }

  @Test
  void waitNoLoopFlagged() {
    RuleTestHarness.assertFixture(new WaitNoLoopRule(), "P3", """
        class P3 {
          void m(Object lock, boolean ready) throws InterruptedException {
            synchronized (lock) {
              if (!ready) {
                lock.wait(); // :: CK-WAIT-NO-LOOP
              }
            }
          }
        }
        """);
  }

  @Test
  void waitInLoopAllowed() {
    RuleTestHarness.assertFixture(new WaitNoLoopRule(), "N3", """
        class N3 {
          void m(Object lock, boolean ready) throws InterruptedException {
            synchronized (lock) {
              while (!ready) {
                lock.wait();
              }
            }
          }
        }
        """);
  }

  @Test
  void containsInLoopFlagged() {
    RuleTestHarness.assertFixture(new ContainsInLoopRule(), "P4", """
        import java.util.List;
        class P4 {
          int m(List<String> big, List<String> seen) {
            int dupes = 0;
            for (String item : big) {
              if (seen.contains(item)) { // :: CK-CONTAINS-IN-LOOP
                dupes++;
              }
            }
            return dupes;
          }
        }
        """);
  }

  @Test
  void hashSetLookupAllowed() {
    RuleTestHarness.assertFixture(new ContainsInLoopRule(), "N4", """
        import java.util.HashSet;
        import java.util.List;
        import java.util.Set;
        class N4 {
          int m(List<String> big, List<String> seen) {
            Set<String> seenSet = new HashSet<>(seen);
            int dupes = 0;
            for (String item : big) {
              if (seenSet.contains(item)) {
                dupes++;
              }
            }
            return dupes;
          }
        }
        """);
  }
}
