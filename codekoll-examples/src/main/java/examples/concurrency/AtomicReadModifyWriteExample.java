package examples.concurrency;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Example for rule {@code CK-ATOMIC-READ-MODIFY-WRITE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(long)} adds to an {@code AtomicLong} with
 * {@code bytes.set(bytes.get() + delta)}. That is two atomic operations with a gap in the
 * middle, not one atomic update — the type is doing nothing the code actually relies on.
 *
 * <p><b>What happens at runtime:</b> two threads read the same value, both add their delta,
 * and both write their result: one contribution is silently overwritten. Nothing throws, no
 * test fails, and single-threaded runs are always exact. The only symptom is a total that
 * drifts low under load — the exact failure the {@code Atomic} type was chosen to prevent, and
 * the reason a reviewer skims past this line as "already thread-safe".
 *
 * <p><b>How to fix it:</b> use the single-call form, as {@link #fixed(long)} does:
 * {@code addAndGet(delta)}, {@code incrementAndGet()}, or the general
 * {@code updateAndGet(current -> ...)}, which re-reads and retries on contention rather than
 * losing the update.
 */
public class AtomicReadModifyWriteExample {

  private final AtomicLong bytes = new AtomicLong();

  public long buggy(long delta) {
    bytes.set(bytes.get() + delta); // :: CK-ATOMIC-READ-MODIFY-WRITE
    return bytes.get();
  }

  public long fixed(long delta) {
    return bytes.addAndGet(delta);
  }
}
