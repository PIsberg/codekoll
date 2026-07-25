package examples.concurrency;

/**
 * Example for rule {@code CK-WAIT-NO-LOOP}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} waits inside an {@code if}, not a
 * {@code while}.
 *
 * <p><b>What happens at runtime:</b> {@code wait()} can return SPURIOUSLY — with no notify
 * and the condition still false. After an {@code if} guard the thread proceeds as though
 * {@code ready} is true when it may not be, consuming an item that was never produced and
 * corrupting the queue. The condition must be re-checked on every wakeup.
 *
 * <p><b>How to fix it:</b> wait in a {@code while} loop, as {@link #fixed()} does.
 */
public class WaitNoLoopExample {

  private final Object lock = new Object();
  private boolean ready;

  public void buggy() throws InterruptedException {
    synchronized (lock) {
      if (!ready) {
        lock.wait(); // :: CK-WAIT-NO-LOOP
      }
      consume();
    }
  }

  public void fixed() throws InterruptedException {
    synchronized (lock) {
      while (!ready) {
        lock.wait();
      }
      consume();
    }
  }

  private void consume() {
    ready = false;
  }
}
