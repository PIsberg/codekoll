package io.codekoll.rules.concurrency;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

/** CK-COMMONPOOL-BLOCKING (issue #119). */
class CommonPoolBlockingRuleTest {

  @Test
  void blockingOnTheCommonPoolFlagged() {
    RuleTestHarness.assertFixture(new CommonPoolBlockingRule(), "P1", """
        import java.io.IOException;
        import java.io.UncheckedIOException;
        import java.util.List;
        import java.util.concurrent.CompletableFuture;
        class P1 {
          static String fetch(String url) throws IOException {
            return url;
          }
          CompletableFuture<String> supply() {
            return CompletableFuture.supplyAsync(() -> {
              try {
                Thread.sleep(100); // :: CK-COMMONPOOL-BLOCKING
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return "x";
            });
          }
          CompletableFuture<Void> run() {
            return CompletableFuture.runAsync(() -> {
              try {
                fetch("a"); // :: CK-COMMONPOOL-BLOCKING
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
          }
          CompletableFuture<String> stage(CompletableFuture<String> f) {
            return f.thenApplyAsync(url -> {
              try {
                return fetch(url); // :: CK-COMMONPOOL-BLOCKING
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
          }
          void parallel(List<String> urls) {
            urls.parallelStream().map(url -> {
              try {
                return fetch(url); // :: CK-COMMONPOOL-BLOCKING
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            }).toList();
          }
          void madeParallel(List<String> urls) {
            urls.stream().parallel().forEach(url -> {
              try {
                fetch(url); // :: CK-COMMONPOOL-BLOCKING
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
          }
        }
        """);
  }

  @Test
  void ownExecutorSequentialOrNonBlockingAllowed() {
    RuleTestHarness.assertFixture(new CommonPoolBlockingRule(), "N1", """
        import java.io.IOException;
        import java.io.UncheckedIOException;
        import java.util.List;
        import java.util.concurrent.CompletableFuture;
        import java.util.concurrent.ExecutorService;
        class N1 {
          static String fetch(String url) throws IOException {
            return url;
          }
          static String safely(String url) {
            try {
              return fetch(url);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          }
          CompletableFuture<String> ownPool(ExecutorService io) {
            return CompletableFuture.supplyAsync(() -> safely("a"), io)
                .thenApplyAsync(url -> {
                  try {
                    return fetch(url);
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                }, io);
          }
          void sequential(List<String> urls) {
            urls.stream().forEach(url -> {
              try {
                fetch(url);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
            urls.parallelStream().sequential().forEach(url -> {
              try {
                fetch(url);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
          }
          long cpuOnly(List<String> words) {
            return words.parallelStream().mapToInt(String::length).sum()
                + CompletableFuture.supplyAsync(() -> words.size()).join();
          }
          CompletableFuture<Void> nested(ExecutorService io) {
            return CompletableFuture.runAsync(() -> io.execute(() -> {
              try {
                Thread.sleep(10);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }));
          }
        }
        """);
  }
}
