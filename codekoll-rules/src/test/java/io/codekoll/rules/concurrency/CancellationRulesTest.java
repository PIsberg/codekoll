package io.codekoll.rules.concurrency;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

/** CK-INTERRUPT-NOT-RESTORED and CK-CANCEL-SWALLOWED (issue #123). */
class CancellationRulesTest {

  @Test
  void interruptLoggedOrWrappedFlagged() {
    RuleTestHarness.assertFixture(new InterruptNotRestoredRule(), "P1", """
        import java.util.concurrent.BlockingQueue;
        import java.util.concurrent.ExecutionException;
        import java.util.concurrent.Future;
        class P1 {
          String logged(BlockingQueue<String> queue) {
            try {
              return queue.take();
            } catch (InterruptedException e) { // :: CK-INTERRUPT-NOT-RESTORED
              System.err.println("interrupted: " + e);
              return "";
            }
          }
          String wrapped(BlockingQueue<String> queue) {
            try {
              return queue.take();
            } catch (InterruptedException e) { // :: CK-INTERRUPT-NOT-RESTORED
              throw new IllegalStateException(e);
            }
          }
          String multi(Future<String> future) {
            try {
              return future.get();
            } catch (InterruptedException | ExecutionException e) { // :: CK-INTERRUPT-NOT-RESTORED
              return "";
            }
          }
          void empty() {
            try {
              Thread.sleep(10);
            } catch (InterruptedException ignored) { // :: CK-INTERRUPT-NOT-RESTORED
            }
          }
        }
        """);
  }

  @Test
  void workerLoopThatKeepsGoingFlagged() {
    // run() is a thread entry point, but this loop carries on after the catch: the thread was
    // asked to stop, cleared the flag and keeps working.
    RuleTestHarness.assertFixture(new InterruptNotRestoredRule(), "P2", """
        import java.util.concurrent.BlockingQueue;
        class P2 implements Runnable {
          private final BlockingQueue<Runnable> queue;
          P2(BlockingQueue<Runnable> queue) {
            this.queue = queue;
          }
          @Override
          public void run() {
            while (true) {
              try {
                queue.take().run();
              } catch (InterruptedException e) { // :: CK-INTERRUPT-NOT-RESTORED
                System.err.println("ignored");
              }
            }
          }
        }
        """);
  }

  @Test
  void restoredRethrownOrAtThreadEndAllowed() {
    RuleTestHarness.assertFixture(new InterruptNotRestoredRule(), "N1", """
        import java.util.concurrent.BlockingQueue;
        class N1 {
          String restored(BlockingQueue<String> queue) {
            try {
              return queue.take();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
          }
          String rethrown(BlockingQueue<String> queue) throws InterruptedException {
            try {
              return queue.take();
            } catch (InterruptedException e) {
              System.err.println("giving up");
              throw e;
            }
          }
          String translated(BlockingQueue<String> queue) throws InterruptedException {
            try {
              return queue.take();
            } catch (InterruptedException e) {
              throw new InterruptedException("while waiting for work");
            }
          }
          static void main(String[] args) {
            try {
              Thread.sleep(10);
            } catch (InterruptedException e) {
              System.err.println("bye");
            }
          }
          static class Worker implements Runnable {
            private final BlockingQueue<Runnable> queue;
            Worker(BlockingQueue<Runnable> queue) {
              this.queue = queue;
            }
            @Override
            public void run() {
              while (true) {
                try {
                  queue.take().run();
                } catch (InterruptedException e) {
                  return;
                }
              }
            }
          }
          static class Breaker implements Runnable {
            private final BlockingQueue<Runnable> queue;
            Breaker(BlockingQueue<Runnable> queue) {
              this.queue = queue;
            }
            @Override
            public void run() {
              while (true) {
                try {
                  queue.take().run();
                } catch (InterruptedException e) {
                  break;
                }
              }
            }
          }
          Thread lambda() {
            return new Thread(() -> {
              try {
                Thread.sleep(10);
              } catch (InterruptedException e) {
                System.err.println("stopping");
              }
            });
          }
        }
        """);
  }

  @Test
  void entryPointThatCarriesOnFlagged() {
    // A run() whose try is not its last statement: the code after the catch runs with the
    // interrupt already cleared.
    RuleTestHarness.assertFixture(new InterruptNotRestoredRule(), "P5", """
        class P5 implements Runnable {
          @Override
          public void run() {
            try {
              Thread.sleep(10);
            } catch (InterruptedException e) { // :: CK-INTERRUPT-NOT-RESTORED
              System.err.println("woke early");
            }
            System.out.println("carrying on");
          }
        }
        """);
  }

  @Test
  void broadCatchAroundInterruptibleCallFlagged() {
    RuleTestHarness.assertFixture(new CancelSwallowedRule(), "P3", """
        import java.util.concurrent.Future;
        class P3 {
          String get(Future<String> future) {
            try {
              return future.get();
            } catch (Exception e) { // :: CK-CANCEL-SWALLOWED
              System.err.println("failed: " + e);
              return "";
            }
          }
          void sleep() {
            try {
              Thread.sleep(10);
            } catch (Throwable t) { // :: CK-CANCEL-SWALLOWED
              System.err.println(t);
            }
          }
        }
        """);
  }

  @Test
  void futureFallbackThatIgnoresTheThrowableFlagged() {
    RuleTestHarness.assertFixture(new CancelSwallowedRule(), "P4", """
        import java.util.concurrent.CompletableFuture;
        class P4 {
          CompletableFuture<String> fallback(CompletableFuture<String> f) {
            return f.exceptionally(ex -> null); // :: CK-CANCEL-SWALLOWED
          }
          CompletableFuture<String> handled(CompletableFuture<String> f) {
            return f.handle((value, ex) -> value); // :: CK-CANCEL-SWALLOWED
          }
          CompletableFuture<String> async(CompletableFuture<String> f) {
            return f.exceptionallyAsync(_ -> "fallback"); // :: CK-CANCEL-SWALLOWED
          }
        }
        """);
  }

  @Test
  void cancellationHandledOrUnreachableAllowed() {
    RuleTestHarness.assertFixture(new CancelSwallowedRule(), "N2", """
        import java.util.concurrent.CancellationException;
        import java.util.concurrent.CompletableFuture;
        import java.util.concurrent.ExecutorService;
        import java.util.concurrent.Future;
        class N2 {
          String narrowFirst(Future<String> future) {
            try {
              return future.get();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return "";
            } catch (Exception e) {
              return "";
            }
          }
          String rethrows(Future<String> future) {
            try {
              return future.get();
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          }
          String inspects(Future<String> future) {
            try {
              return future.get();
            } catch (Exception e) {
              if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
              }
              return "";
            }
          }
          String restores(Future<String> future) {
            try {
              return future.get();
            } catch (Exception e) {
              Thread.currentThread().interrupt();
              return "";
            }
          }
          void onlyInLambda(ExecutorService pool) {
            try {
              pool.submit(() -> {
                Thread.sleep(10);
                return null;
              });
            } catch (Exception e) {
              System.err.println(e);
            }
          }
          int noInterruptibleCall(String s) {
            try {
              return Integer.parseInt(s);
            } catch (Exception e) {
              return 0;
            }
          }
          String join(CompletableFuture<String> f) {
            try {
              return f.join();
            } catch (Exception e) {
              return "";
            }
          }
          CompletableFuture<String> reads(CompletableFuture<String> f) {
            return f.exceptionally(ex -> ex instanceof CancellationException ? "cancelled" : "");
          }
          CompletableFuture<String> handled(CompletableFuture<String> f) {
            return f.handle((value, ex) -> ex == null ? value : "failed: " + ex);
          }
          CompletableFuture<String> observed(CompletableFuture<String> f) {
            return f.whenComplete((value, ex) -> System.out.println(value));
          }
          CompletableFuture<String> methodRef(CompletableFuture<String> f) {
            return f.exceptionally(N2::describe);
          }
          static String describe(Throwable t) {
            return t.toString();
          }
          Thread entry() {
            return new Thread(() -> {
              try {
                Thread.sleep(10);
              } catch (Exception e) {
                System.err.println("stopping");
              }
            });
          }
        }
        """);
  }
}
