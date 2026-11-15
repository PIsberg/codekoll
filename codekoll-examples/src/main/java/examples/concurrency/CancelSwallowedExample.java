package examples.concurrency;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Example for rule {@code CK-CANCEL-SWALLOWED}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Future)} waits for a price quote inside a
 * {@code catch (Exception)} that falls back to a default, and {@link #buggyFallback} turns any
 * failure of a quote pipeline into the default without looking at what failed.
 *
 * <p><b>What happens at runtime:</b> the task running this code is cancelled the way every
 * executor and {@code StructuredTaskScope} cancels: by interrupting it. {@code future.get()}
 * throws {@code InterruptedException}, the broad catch takes it for an ordinary failure, clears
 * the flag and returns the default, and the task carries on to its next step. The scope's
 * {@code join()} or the executor's shutdown waits for work that was supposed to stop. The
 * fallback does the same to a cancelled pipeline: {@code CancellationException} becomes a
 * normal price.
 *
 * <p><b>How to fix it:</b> handle {@code InterruptedException} first and restore the flag, and
 * let cancellation through the fallback, as {@link #fixed(Future)} and {@link #fixedFallback} do.
 */
public class CancelSwallowedExample {

  private static final long DEFAULT_PRICE = 100;

  public long buggy(Future<Long> quote) {
    try {
      return quote.get();
    } catch (Exception e) { // :: CK-CANCEL-SWALLOWED
      return DEFAULT_PRICE;
    }
  }

  public CompletableFuture<Long> buggyFallback(CompletableFuture<Long> quote) {
    return quote.exceptionally(failure -> DEFAULT_PRICE); // :: CK-CANCEL-SWALLOWED
  }

  public long fixed(Future<Long> quote) {
    try {
      return quote.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return DEFAULT_PRICE;
    } catch (ExecutionException e) {
      return DEFAULT_PRICE;
    }
  }

  public CompletableFuture<Long> fixedFallback(CompletableFuture<Long> quote) {
    return quote.exceptionally(failure -> {
      if (failure instanceof java.util.concurrent.CancellationException cancelled) {
        throw cancelled;
      }
      return DEFAULT_PRICE;
    });
  }
}
