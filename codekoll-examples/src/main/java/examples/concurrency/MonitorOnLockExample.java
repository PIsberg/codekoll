package examples.concurrency;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Example for rule {@code CK-MONITOR-ON-LOCK}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} uses {@code synchronized (lock)} on a
 * {@code ReentrantLock}, while other code paths use {@code lock.lock()}.
 *
 * <p><b>What happens at runtime:</b> the object's monitor and its Lock state are completely
 * independent mechanisms. Threads inside {@code synchronized (lock)} and threads inside
 * {@code lock.lock()} exclude only among themselves — the two groups run their "critical
 * sections" concurrently, producing data races behind a lock that looks correct in review.
 *
 * <p><b>How to fix it:</b> use the Lock API consistently, as {@link #fixed()} does.
 */
public class MonitorOnLockExample {

  private final ReentrantLock lock = new ReentrantLock();
  private int balance;

  public void buggy() {
    synchronized (lock) { // :: CK-MONITOR-ON-LOCK
      balance++;
    }
  }

  public void fixed() {
    lock.lock();
    try {
      balance++;
    } finally {
      lock.unlock();
    }
  }
}
