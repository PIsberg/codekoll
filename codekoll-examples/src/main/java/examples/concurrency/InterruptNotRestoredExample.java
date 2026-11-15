package examples.concurrency;

import java.util.concurrent.BlockingQueue;

/**
 * Example for rule {@code CK-INTERRUPT-NOT-RESTORED}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(BlockingQueue)} waits for the next job, and when the
 * wait is interrupted it logs the exception and returns a "no job" marker.
 *
 * <p><b>What happens at runtime:</b> throwing {@code InterruptedException} cleared the thread's
 * interrupt flag, and nothing sets it again. The worker loop that called this method sees an
 * ordinary empty result, checks {@code isInterrupted()} (false) and waits again.
 * {@code executor.shutdownNow()} was the interrupt; the pool now sits in
 * {@code awaitTermination} until it times out.
 *
 * <p><b>How to fix it:</b> restore the flag before returning, as {@link #fixed(BlockingQueue)}
 * does, so the caller's loop sees it and stops.
 */
public class InterruptNotRestoredExample {

  public String buggy(BlockingQueue<String> jobs) {
    try {
      return jobs.take();
    } catch (InterruptedException e) { // :: CK-INTERRUPT-NOT-RESTORED
      System.err.println("interrupted while waiting for a job: " + e);
      return "";
    }
  }

  public String fixed(BlockingQueue<String> jobs) {
    try {
      return jobs.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "";
    }
  }
}
