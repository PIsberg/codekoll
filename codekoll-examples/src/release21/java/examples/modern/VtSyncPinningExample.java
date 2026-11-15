package examples.modern;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Example for rule {@code CK-VT-SYNC-PINNING}.
 *
 * <p>This example lives in its own source root, {@code src/release21/java}, analyzed at
 * {@code --release 21}: the rule only applies to projects that still support JDK 21-23.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} waits on a queue while holding the object's monitor,
 * in a service whose requests run on virtual threads.
 *
 * <p><b>What happens at runtime:</b> on JDK 21-23 a virtual thread that blocks inside
 * {@code synchronized} cannot unmount; it pins its carrier thread for the whole wait. There is
 * one carrier per core. Eight requests waiting here on an eight-core machine stop every other
 * virtual thread in the JVM, including the one that would put the next element on the queue:
 * the service hangs with almost no CPU in use and nothing in the logs.
 *
 * <p><b>How to fix it:</b> guard the section with a {@link ReentrantLock}, which virtual threads
 * can block on without pinning, as {@link #fixed()} does (or require Java 24, JEP 491).
 */
public class VtSyncPinningExample {

  private final BlockingQueue<String> work;
  private final ReentrantLock lock = new ReentrantLock();
  private int handled;

  public VtSyncPinningExample(BlockingQueue<String> work) {
    this.work = work;
  }

  public synchronized String buggy() throws InterruptedException {
    handled++;
    return work.take(); // :: CK-VT-SYNC-PINNING
  }

  public String fixed() throws InterruptedException {
    lock.lock();
    try {
      handled++;
      return work.take();
    } finally {
      lock.unlock();
    }
  }
}
