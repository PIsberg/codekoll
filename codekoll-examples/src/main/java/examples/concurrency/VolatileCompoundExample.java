package examples.concurrency;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example for rule {@code CK-VOLATILE-COMPOUND}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} increments a {@code volatile} counter with
 * {@code ++}.
 *
 * <p><b>What happens at runtime:</b> {@code volatile} guarantees visibility, not atomicity.
 * {@code count++} is read-add-write; two threads interleaving both read the same value and
 * one increment vanishes. The counter drifts low under load — discovered months later when
 * the metrics don't add up, never as an exception.
 *
 * <p><b>How to fix it:</b> an {@code AtomicInteger}, as {@link #fixed()} does.
 */
public class VolatileCompoundExample {

  private volatile int count;
  private final AtomicInteger safeCount = new AtomicInteger();

  public void buggy() {
    count++; // :: CK-VOLATILE-COMPOUND
  }

  public void fixed() {
    safeCount.incrementAndGet();
  }
}
