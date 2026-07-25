package examples.modern;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Example for rule {@code CK-VT-POOLING}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} "modernizes" a thread pool by plugging a
 * virtual-thread factory into {@code newFixedThreadPool(8)}.
 *
 * <p><b>What happens at runtime:</b> the pool still caps concurrency at 8 — virtual
 * threads' entire benefit (cheap, unpooled, millions if needed) is silently discarded.
 * Blocking I/O tasks queue behind the 8 slots exactly as before; the migration to virtual
 * threads changed nothing but the thread names.
 *
 * <p><b>How to fix it:</b> one virtual thread per task, no pool, as {@link #fixed()} does.
 */
public class VtPoolingExample {

  public ExecutorService buggy() {
    return Executors.newFixedThreadPool(8, Thread.ofVirtual().factory()); // :: CK-VT-POOLING
  }

  public ExecutorService fixed() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
