package io.codekoll.rules.modern;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ModernBatch2RulesTest {

  @Test
  void vtPoolingFlagged() {
    RuleTestHarness.assertFixture(new VtPoolingRule(), "P1", """
        import java.util.concurrent.ExecutorService;
        import java.util.concurrent.Executors;
        class P1 {
          ExecutorService m() {
            return Executors.newFixedThreadPool(8, Thread.ofVirtual().factory()); // :: CK-VT-POOLING
          }
        }
        """);
  }

  @Test
  void perTaskExecutorAndPlatformPoolAllowed() {
    RuleTestHarness.assertFixture(new VtPoolingRule(), "N1", """
        import java.util.concurrent.ExecutorService;
        import java.util.concurrent.Executors;
        class N1 {
          ExecutorService[] m() {
            return new ExecutorService[] {
              Executors.newVirtualThreadPerTaskExecutor(),
              Executors.newFixedThreadPool(8),
            };
          }
        }
        """);
  }

  @Test
  void vtDaemonAndPriorityFlagged() {
    RuleTestHarness.assertFixture(new VtDaemonPriorityRule(), "P2", """
        class P2 {
          void m(Runnable task) {
            Thread.ofVirtual().unstarted(task).setDaemon(false); // :: CK-VT-DAEMON-PRIORITY
            Thread.startVirtualThread(task).setPriority(10); // :: CK-VT-DAEMON-PRIORITY
          }
        }
        """);
  }

  @Test
  void platformThreadTuningAllowed() {
    RuleTestHarness.assertFixture(new VtDaemonPriorityRule(), "N2", """
        class N2 {
          void m(Runnable task) {
            Thread platform = new Thread(task);
            platform.setDaemon(false);
            platform.setPriority(Thread.MAX_PRIORITY);
            Thread.ofVirtual().unstarted(task).setDaemon(true);
          }
        }
        """);
  }

  @Test
  void durationCalendarFlagged() {
    RuleTestHarness.assertFixture(new DurationCalendarRule(), "P3", """
        import java.time.Duration;
        import java.time.ZonedDateTime;
        class P3 {
          ZonedDateTime m(ZonedDateTime run) {
            return run.plus(Duration.ofDays(1)); // :: CK-DURATION-CALENDAR
          }
        }
        """);
  }

  @Test
  void plusDaysAndExactElapsedAllowed() {
    RuleTestHarness.assertFixture(new DurationCalendarRule(), "N3", """
        import java.time.Duration;
        import java.time.Instant;
        import java.time.ZonedDateTime;
        class N3 {
          Object[] m(ZonedDateTime run, Instant point) {
            return new Object[] {
              run.plusDays(1),
              point.plus(Duration.ofDays(1)),
              run.plus(Duration.ofSeconds(30)),
            };
          }
        }
        """);
  }
}
