package io.codekoll.rules.modern;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

/** CK-VT-THREADLOCAL and CK-VT-SYNC-PINNING (issue #119). */
class VirtualThreadRulesTest {

  /** Blocking under a monitor, marked for the releases where that pins a carrier thread. */
  private static final String PINNING = """
      import java.io.IOException;
      import java.io.InputStream;
      import java.util.concurrent.BlockingQueue;
      import java.util.concurrent.LinkedBlockingQueue;
      class P2 {
        private final Object lock = new Object();
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        void waitIn() throws InterruptedException {
          synchronized (lock) {
            lock.wait(); // :: CK-VT-SYNC-PINNING
          }
        }
        synchronized String take() throws InterruptedException {
          return queue.take(); // :: CK-VT-SYNC-PINNING
        }
        int io(InputStream in) throws IOException {
          synchronized (lock) {
            return in.read(); // :: CK-VT-SYNC-PINNING
          }
        }
      }
      """;

  @Test
  void threadLocalInVirtualThreadTaskFlagged() {
    RuleTestHarness.assertFixture(new VtThreadLocalRule(), "P1", """
        import java.text.SimpleDateFormat;
        import java.util.Date;
        import java.util.concurrent.ExecutorService;
        import java.util.concurrent.Executors;
        class P1 {
          static final ThreadLocal<SimpleDateFormat> FORMAT =
              ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy"));
          static final ThreadLocal<String> USER = new ThreadLocal<>();
          void start() {
            Thread.startVirtualThread(() -> FORMAT.get().format(new Date())); // :: CK-VT-THREADLOCAL
          }
          void builder() {
            Thread.ofVirtual().start(() -> USER.set("alice")); // :: CK-VT-THREADLOCAL
          }
          void executor() {
            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
              pool.submit(() -> {
                USER.set("bob"); // :: CK-VT-THREADLOCAL
                return USER.get(); // :: CK-VT-THREADLOCAL
              });
            }
          }
          void local() {
            ExecutorService pool = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
            pool.execute(() -> USER.remove()); // :: CK-VT-THREADLOCAL
          }
          void chain() {
            Executors.newVirtualThreadPerTaskExecutor().execute(() -> USER.remove()); // :: CK-VT-THREADLOCAL
          }
        }
        """);
  }

  @Test
  void threadLocalOnPlatformThreadsOrOutsideTheTaskAllowed() {
    RuleTestHarness.assertFixture(new VtThreadLocalRule(), "N1", """
        import java.util.concurrent.ExecutorService;
        import java.util.concurrent.Executors;
        class N1 {
          static final ThreadLocal<String> USER = new ThreadLocal<>();
          static final ScopedValue<String> CURRENT = ScopedValue.newInstance();
          void platform() {
            new Thread(() -> USER.set("x")).start();
            Thread.ofPlatform().start(() -> USER.get());
          }
          void pool(ExecutorService given) {
            ExecutorService fixed = Executors.newFixedThreadPool(4);
            fixed.submit(() -> USER.get());
            given.execute(() -> USER.remove());
          }
          void scoped() {
            Thread.startVirtualThread(() -> ScopedValue.where(CURRENT, "alice").run(this::work));
          }
          void outside() {
            USER.set("before");
            Thread.startVirtualThread(this::work);
          }
          void nested(ExecutorService platformPool) {
            Thread.startVirtualThread(() -> platformPool.execute(() -> USER.get()));
          }
          void work() {}
        }
        """);
  }

  @Test
  void blockingUnderMonitorFlaggedBeforeJep491() {
    RuleTestHarness.assertFixture(new VtSyncPinningRule(), "P2", PINNING, 21);
    RuleTestHarness.assertFixture(new VtSyncPinningRule(), "P2", PINNING, 23);
  }

  @Test
  void blockingUnderMonitorSilentFromJava24AndWithoutVirtualThreads() {
    String unmarked = PINNING.replace("// :: CK-VT-SYNC-PINNING", "");
    RuleTestHarness.assertFixture(new VtSyncPinningRule(), "P2", unmarked, 24);
    RuleTestHarness.assertFixture(new VtSyncPinningRule(), "P2", unmarked, 25);
    RuleTestHarness.assertFixture(new VtSyncPinningRule(), "P2", unmarked, 17);
  }

  @Test
  void monitorWithoutBlockingOrBlockingOutsideAllowed() {
    RuleTestHarness.assertFixture(new VtSyncPinningRule(), "N2", """
        import java.util.concurrent.locks.ReentrantLock;
        class N2 {
          private final Object lock = new Object();
          private final ReentrantLock reentrant = new ReentrantLock();
          private int count;
          void counter() {
            synchronized (lock) {
              count++;
            }
          }
          void outside() throws InterruptedException {
            synchronized (lock) {
              count++;
            }
            Thread.sleep(10);
          }
          void deferred() {
            synchronized (lock) {
              Thread.startVirtualThread(() -> {
                try {
                  Thread.sleep(10);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
            }
          }
          void lockInstead() throws InterruptedException {
            reentrant.lock();
            try {
              Thread.sleep(10);
            } finally {
              reentrant.unlock();
            }
          }
        }
        """, 21);
  }
}
