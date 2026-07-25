package examples.concurrency;

/**
 * Example for rule {@code CK-SYNC-ON-VALUE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} synchronizes on a String constant.
 *
 * <p><b>What happens at runtime:</b> string literals are interned — every class in the JVM
 * that locks on {@code "SESSION_LOCK"} locks the <em>same object</em>. Unrelated libraries
 * contend with (or deadlock against) this code, and profilers show contention on a lock
 * that "nobody else uses".
 *
 * <p><b>How to fix it:</b> a dedicated private lock object, as {@link #fixed()} does.
 */
public class SyncOnValueExample {

  private static final String LOCK_NAME = "SESSION_LOCK";
  private final Object lock = new Object();
  private int sessions;

  public void buggy() {
    synchronized (LOCK_NAME) { // :: CK-SYNC-ON-VALUE
      sessions++;
    }
  }

  public void fixed() {
    synchronized (lock) {
      sessions++;
    }
  }
}
