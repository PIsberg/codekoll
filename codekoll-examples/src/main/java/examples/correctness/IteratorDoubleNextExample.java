package examples.correctness;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Example for rule {@code CK-ITERATOR-DOUBLE-NEXT}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Iterator)} reads a flat token stream as name/value
 * pairs, so each pass through the loop calls {@code tokens.next()} twice — but the loop is
 * guarded by a single {@code tokens.hasNext()}, which promises exactly one more element.
 *
 * <p><b>What happens at runtime:</b> for an even number of tokens it works perfectly, which is
 * what every hand-written fixture has. Hand it a stream with a trailing name and no value —
 * one truncated request, one malformed header line — and the second {@code next()} throws
 * {@code NoSuchElementException}, from a loop that appears to be checking before it reads.
 *
 * <p><b>How to fix it:</b> read once per guard. If pairwise consumption is genuinely intended,
 * give the second read its own {@code if (tokens.hasNext())}, as {@link #fixed(Iterator)} does,
 * and decide explicitly what a dangling name should mean.
 */
public class IteratorDoubleNextExample {

  public Map<String, String> buggy(Iterator<String> tokens) {
    Map<String, String> headers = new LinkedHashMap<>();
    while (tokens.hasNext()) {
      String name = tokens.next();
      String value = tokens.next(); // :: CK-ITERATOR-DOUBLE-NEXT
      headers.put(name, value);
    }
    return headers;
  }

  public Map<String, String> fixed(Iterator<String> tokens) {
    Map<String, String> headers = new LinkedHashMap<>();
    while (tokens.hasNext()) {
      String name = tokens.next();
      if (tokens.hasNext()) {
        headers.put(name, tokens.next());
      }
    }
    return headers;
  }
}
