package io.codekoll.rules.concurrency;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ConcurrencyBatch2RulesTest {

  @Test
  void dclNonVolatileFlagged() {
    RuleTestHarness.assertFixture(new DclNoVolatileRule(), "P1", """
        class P1 {
          private static P1 instance;
          private final Object lock = new Object();
          P1 get() {
            if (instance == null) { // :: CK-DCL-NO-VOLATILE
              synchronized (lock) {
                if (instance == null) {
                  instance = new P1();
                }
              }
            }
            return instance;
          }
        }
        """);
  }

  @Test
  void volatileDclAllowed() {
    RuleTestHarness.assertFixture(new DclNoVolatileRule(), "N1", """
        class N1 {
          private static volatile N1 instance;
          private final Object lock = new Object();
          N1 get() {
            if (instance == null) {
              synchronized (lock) {
                if (instance == null) {
                  instance = new N1();
                }
              }
            }
            return instance;
          }
        }
        """);
  }

  @Test
  void concurrentModFlagged() {
    RuleTestHarness.assertFixture(new ConcurrentModRule(), "P2", """
        import java.util.List;
        class P2 {
          void m(List<String> items) {
            for (String item : items) {
              if (item.isEmpty()) {
                items.remove(item); // :: CK-CONCURRENT-MOD
              }
            }
          }
        }
        """);
  }

  @Test
  void removeIfAndOtherCollectionAllowed() {
    RuleTestHarness.assertFixture(new ConcurrentModRule(), "N2", """
        import java.util.ArrayList;
        import java.util.List;
        class N2 {
          void m(List<String> items) {
            items.removeIf(String::isEmpty);
            List<String> keep = new ArrayList<>();
            for (String item : items) {
              if (!item.isEmpty()) {
                keep.add(item);
              }
            }
          }
        }
        """);
  }

  @Test
  void lockNoFinallyFlagged() {
    RuleTestHarness.assertFixture(new LockNoFinallyRule(), "P3", """
        import java.util.concurrent.locks.ReentrantLock;
        class P3 {
          private final ReentrantLock lock = new ReentrantLock();
          void m() {
            lock.lock(); // :: CK-LOCK-NO-FINALLY
            doWork();
            lock.unlock();
          }
          void doWork() {
          }
        }
        """);
  }

  @Test
  void lockWithTryFinallyAllowed() {
    RuleTestHarness.assertFixture(new LockNoFinallyRule(), "N3", """
        import java.util.concurrent.locks.ReentrantLock;
        class N3 {
          private final ReentrantLock lock = new ReentrantLock();
          void m() {
            lock.lock();
            try {
              doWork();
            } finally {
              lock.unlock();
            }
          }
          void doWork() {
          }
        }
        """);
  }
}
