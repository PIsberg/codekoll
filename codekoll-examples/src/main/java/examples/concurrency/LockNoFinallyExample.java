package examples.concurrency;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Example for rule {@code CK-LOCK-NO-FINALLY}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} calls {@code unlock()} at the end of the method
 * instead of in a {@code finally}.
 *
 * <p><b>What happens at runtime:</b> if {@code updateBalance()} throws, control skips the
 * {@code unlock()} and the lock is never released. Every thread that later needs it blocks
 * forever — one uncaught exception deadlocks the whole subsystem. Unlike {@code
 * synchronized}, an explicit Lock has no automatic release.
 *
 * <p><b>How to fix it:</b> put {@code unlock()} in a {@code finally}, as {@link #fixed()}
 * does — it then runs on every exit path.
 */
public class LockNoFinallyExample {

  private final ReentrantLock lock = new ReentrantLock();
  private int balance;

  public void buggy() {
    lock.lock(); // :: CK-LOCK-NO-FINALLY
    updateBalance();
    lock.unlock();
  }

  public void fixed() {
    lock.lock();
    try {
      updateBalance();
    } finally {
      lock.unlock();
    }
  }

  private void updateBalance() {
    balance++;
  }
}
