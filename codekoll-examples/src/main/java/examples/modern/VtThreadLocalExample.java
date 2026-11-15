package examples.modern;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Example for rule {@code CK-VT-THREADLOCAL}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(long)} formats a timestamp on a virtual thread with a
 * {@code SimpleDateFormat} cached in a {@code ThreadLocal}, the classic way to share a
 * non-thread-safe formatter across a fixed pool.
 *
 * <p><b>What happens at runtime:</b> the cache only pays off when a thread runs many tasks. A
 * virtual thread runs one task and is discarded, starting with empty thread-locals, so every
 * task builds a new {@code SimpleDateFormat} (one of the more expensive JDK objects to create)
 * and throws it away. The migration to virtual threads quietly turned a cache into an
 * allocation per request; with a million concurrent tasks it is also a million formatters live
 * at once.
 *
 * <p><b>How to fix it:</b> use an immutable, shareable {@link DateTimeFormatter}, as
 * {@link #fixed(long)} does. For per-task context, use {@code ScopedValue}.
 */
public class VtThreadLocalExample {

  private static final ThreadLocal<SimpleDateFormat> FORMAT =
      ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
  private static final DateTimeFormatter SHARED =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  public Future<String> buggy(long epochMillis) {
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      return pool.submit(() -> FORMAT.get().format(new Date(epochMillis))); // :: CK-VT-THREADLOCAL
    }
  }

  public Future<String> fixed(long epochMillis) {
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      return pool.submit(() -> SHARED.format(Instant.ofEpochMilli(epochMillis)));
    }
  }
}
