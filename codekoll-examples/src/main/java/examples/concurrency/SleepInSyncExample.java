package examples.concurrency;

/**
 * Example for rule {@code CK-SLEEP-IN-SYNC}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} calls {@code Thread.sleep} while holding the
 * monitor (retry back-off inside the lock).
 *
 * <p><b>What happens at runtime:</b> unlike {@code wait()}, {@code sleep()} does NOT
 * release the monitor. Every other thread that needs this lock blocks for the entire sleep
 * — one thread's polite back-off serializes the whole system, visible only as mysterious
 * latency spikes under load.
 *
 * <p><b>How to fix it:</b> sleep outside the critical section, as {@link #fixed()} does.
 */
public class SleepInSyncExample {

  private final Object lock = new Object();
  private int attempts;

  public void buggy() throws InterruptedException {
    synchronized (lock) {
      attempts++;
      Thread.sleep(1000); // :: CK-SLEEP-IN-SYNC
    }
  }

  public void fixed() throws InterruptedException {
    synchronized (lock) {
      attempts++;
    }
    Thread.sleep(1000);
  }
}
