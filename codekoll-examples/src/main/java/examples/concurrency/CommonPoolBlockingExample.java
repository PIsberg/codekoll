package examples.concurrency;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Example for rule {@code CK-COMMONPOOL-BLOCKING}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} looks up a price asynchronously with
 * {@code supplyAsync} and no executor, and the lookup waits on a remote call.
 *
 * <p><b>What happens at runtime:</b> without an executor the task runs on
 * {@code ForkJoinPool.commonPool()}, which has one thread per core and is shared by every
 * parallel stream and default-async future in the JVM. A burst of slow lookups occupies all of
 * them. Unrelated code (a {@code parallelStream()} in a report, another service's async calls)
 * then queues behind remote calls it has nothing to do with, and the latency appears there, far
 * from this line.
 *
 * <p><b>How to fix it:</b> give blocking work its own executor (virtual threads suit I/O), as
 * {@link #fixed(String, ExecutorService)} does.
 */
public class CommonPoolBlockingExample {

  public CompletableFuture<Long> buggy(String sku) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return remotePrice(sku); // :: CK-COMMONPOOL-BLOCKING
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return -1L;
      }
    });
  }

  public CompletableFuture<Long> fixed(String sku, ExecutorService io) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return remotePrice(sku);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return -1L;
      }
    }, io);
  }

  private static long remotePrice(String sku) throws InterruptedException {
    Thread.sleep(200);
    return sku.length() * 100L;
  }
}
