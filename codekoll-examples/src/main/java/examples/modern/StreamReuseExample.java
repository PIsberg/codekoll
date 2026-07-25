package examples.modern;

import java.util.List;
import java.util.stream.Stream;

/**
 * Example for rule {@code CK-STREAM-REUSE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Stream)} runs two terminal operations
 * ({@code count}, then {@code toList}) on the same stream.
 *
 * <p><b>What happens at runtime:</b> a Stream is a one-shot pipeline — the first terminal
 * operation consumes it. The second call throws {@code IllegalStateException: stream has
 * already been operated upon or closed}, every time the code path runs.
 *
 * <p><b>How to fix it:</b> collect once and query the collection, as
 * {@link #fixed(Stream)} does.
 */
public class StreamReuseExample {

  public String buggy(Stream<String> names) {
    long total = names.count();
    List<String> all = names.toList(); // :: CK-STREAM-REUSE
    return total + ":" + all;
  }

  public String fixed(Stream<String> names) {
    List<String> all = names.toList();
    long total = all.size();
    return total + ":" + all;
  }
}
