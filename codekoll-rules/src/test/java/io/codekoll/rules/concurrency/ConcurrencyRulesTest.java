package io.codekoll.rules.concurrency;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ConcurrencyRulesTest {

  @Test
  void syncOnValueFlagged() {
    RuleTestHarness.assertFixture(new SyncOnValueRule(), "P1", """
        class P1 {
          private final String lock = "LOCK";
          private final Integer boxed = 42;
          void m() {
            synchronized (lock) { // :: CK-SYNC-ON-VALUE
              System.gc();
            }
            synchronized (boxed) { // :: CK-SYNC-ON-VALUE
              System.gc();
            }
          }
        }
        """);
  }

  @Test
  void syncOnDedicatedObjectAllowed() {
    RuleTestHarness.assertFixture(new SyncOnValueRule(), "N1", """
        class N1 {
          private final Object lock = new Object();
          void m() {
            synchronized (lock) {
              System.gc();
            }
            synchronized (this) {
              System.gc();
            }
          }
        }
        """);
  }

  @Test
  void monitorOnLockFlagged() {
    RuleTestHarness.assertFixture(new MonitorOnLockRule(), "P2", """
        import java.util.concurrent.locks.ReentrantLock;
        class P2 {
          private final ReentrantLock lock = new ReentrantLock();
          void m() {
            synchronized (lock) { // :: CK-MONITOR-ON-LOCK
              System.gc();
            }
          }
        }
        """);
  }

  @Test
  void properLockUsageAllowed() {
    RuleTestHarness.assertFixture(new MonitorOnLockRule(), "N2", """
        import java.util.concurrent.locks.ReentrantLock;
        class N2 {
          private final ReentrantLock lock = new ReentrantLock();
          void m() {
            lock.lock();
            try {
              System.gc();
            } finally {
              lock.unlock();
            }
          }
        }
        """);
  }

  @Test
  void volatileCompoundFlagged() {
    RuleTestHarness.assertFixture(new VolatileCompoundRule(), "P3", """
        class P3 {
          private volatile int count;
          private volatile long total;
          void m(int n) {
            count++; // :: CK-VOLATILE-COMPOUND
            total += n; // :: CK-VOLATILE-COMPOUND
          }
        }
        """);
  }

  @Test
  void plainFieldAndVolatileWriteAllowed() {
    RuleTestHarness.assertFixture(new VolatileCompoundRule(), "N3", """
        class N3 {
          private int plain;
          private volatile boolean running;
          void m() {
            plain++;
            running = false;
          }
        }
        """);
  }

  @Test
  void staticDateFormatFlagged() {
    RuleTestHarness.assertFixture(new StaticDateFormatRule(), "P4", """
        import java.text.SimpleDateFormat;
        class P4 {
          private static final SimpleDateFormat FORMAT = // :: CK-STATIC-DATEFORMAT
              new SimpleDateFormat("yyyy-MM-dd");
          String m(java.util.Date d) {
            return FORMAT.format(d);
          }
        }
        """);
  }

  @Test
  void instanceFormatAndDateTimeFormatterAllowed() {
    RuleTestHarness.assertFixture(new StaticDateFormatRule(), "N4", """
        import java.text.SimpleDateFormat;
        import java.time.format.DateTimeFormatter;
        class N4 {
          private static final DateTimeFormatter SAFE = DateTimeFormatter.ISO_DATE;
          private final SimpleDateFormat perInstance = new SimpleDateFormat("yyyy");
          String m(java.time.LocalDate d) {
            return SAFE.format(d) + perInstance.hashCode();
          }
        }
        """);
  }

  @Test
  void sleepInSyncFlagged() {
    RuleTestHarness.assertFixture(new SleepInSyncRule(), "P5", """
        class P5 {
          private final Object lock = new Object();
          void m() throws InterruptedException {
            synchronized (lock) {
              Thread.sleep(1000); // :: CK-SLEEP-IN-SYNC
            }
          }
          synchronized void n() throws InterruptedException {
            Thread.sleep(50); // :: CK-SLEEP-IN-SYNC
          }
        }
        """);
  }

  @Test
  void sleepOutsideLockAllowed() {
    RuleTestHarness.assertFixture(new SleepInSyncRule(), "N5", """
        class N5 {
          private final Object lock = new Object();
          void m() throws InterruptedException {
            Thread.sleep(1000);
            synchronized (lock) {
              System.gc();
            }
          }
        }
        """);
  }
}
