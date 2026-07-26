package io.codekoll.rules.concurrency;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class AtomicReadModifyWriteRuleTest {

  private final AtomicReadModifyWriteRule rule = new AtomicReadModifyWriteRule();

  @Test
  void flagsIncrementViaSetGet() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.util.concurrent.atomic.AtomicInteger;
        class P1 {
          private final AtomicInteger processed = new AtomicInteger();
          void m() {
            processed.set(processed.get() + 1); // :: CK-ATOMIC-READ-MODIFY-WRITE
          }
        }
        """);
  }

  @Test
  void flagsAccumulationOnAtomicLong() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import java.util.concurrent.atomic.AtomicLong;
        class P2 {
          private final AtomicLong bytes = new AtomicLong();
          void m(long delta) {
            bytes.set(bytes.get() + delta); // :: CK-ATOMIC-READ-MODIFY-WRITE
          }
        }
        """);
  }

  @Test
  void flagsToggleOnAtomicBoolean() {
    RuleTestHarness.assertFixture(rule, "P3", """
        import java.util.concurrent.atomic.AtomicBoolean;
        class P3 {
          private final AtomicBoolean enabled = new AtomicBoolean();
          void m() {
            enabled.set(!enabled.get()); // :: CK-ATOMIC-READ-MODIFY-WRITE
          }
        }
        """);
  }

  @Test
  void flagsAppendOnAtomicReference() {
    RuleTestHarness.assertFixture(rule, "P4", """
        import java.util.concurrent.atomic.AtomicReference;
        class P4 {
          private final AtomicReference<String> trail = new AtomicReference<>("");
          void m(String step) {
            trail.set(trail.get() + step); // :: CK-ATOMIC-READ-MODIFY-WRITE
          }
        }
        """);
  }

  @Test
  void flagsLazySetForm() {
    RuleTestHarness.assertFixture(rule, "P5", """
        import java.util.concurrent.atomic.AtomicInteger;
        class P5 {
          private final AtomicInteger seen = new AtomicInteger();
          void m() {
            seen.lazySet(seen.get() + 1); // :: CK-ATOMIC-READ-MODIFY-WRITE
          }
        }
        """);
  }

  @Test
  void allowsTheAtomicOneCallForms() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.util.concurrent.atomic.AtomicInteger;
        class N1 {
          private final AtomicInteger processed = new AtomicInteger();
          void m(int delta) {
            processed.incrementAndGet();
            processed.addAndGet(delta);
            processed.updateAndGet(current -> current + delta);
          }
        }
        """);
  }

  @Test
  void allowsWritingAnIndependentValue() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import java.util.concurrent.atomic.AtomicInteger;
        class N2 {
          private final AtomicInteger processed = new AtomicInteger();
          void m(int value) {
            processed.set(0);
            processed.set(value);
          }
        }
        """);
  }

  @Test
  void allowsReadingADifferentAtomic() {
    RuleTestHarness.assertFixture(rule, "N3", """
        import java.util.concurrent.atomic.AtomicInteger;
        class N3 {
          private final AtomicInteger source = new AtomicInteger();
          private final AtomicInteger mirror = new AtomicInteger();
          void m() {
            mirror.set(source.get() + 1);
          }
        }
        """);
  }

  @Test
  void allowsReadModifyWriteInsideASynchronizedBlock() {
    RuleTestHarness.assertFixture(rule, "N4", """
        import java.util.concurrent.atomic.AtomicInteger;
        class N4 {
          private final Object lock = new Object();
          private final AtomicInteger processed = new AtomicInteger();
          void m() {
            synchronized (lock) {
              processed.set(processed.get() + 1);
            }
          }
        }
        """);
  }

  @Test
  void allowsReadModifyWriteInASynchronizedMethod() {
    RuleTestHarness.assertFixture(rule, "N5", """
        import java.util.concurrent.atomic.AtomicInteger;
        class N5 {
          private final AtomicInteger processed = new AtomicInteger();
          synchronized void m() {
            processed.set(processed.get() + 1);
          }
        }
        """);
  }

  @Test
  void allowsSetGetOnAnOrdinaryHolder() {
    RuleTestHarness.assertFixture(rule, "N6", """
        class N6 {
          static final class Counter {
            private int value;
            int get() {
              return value;
            }
            void set(int v) {
              value = v;
            }
          }
          private final Counter counter = new Counter();
          void m() {
            counter.set(counter.get() + 1);
          }
        }
        """);
  }
}
