package examples.nullness;

import java.util.Map;

/**
 * Example for rule {@code CK-UNBOX-NPE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Map, String)} assigns {@code map.get(key)}
 * straight into a primitive {@code int}.
 *
 * <p><b>What happens at runtime:</b> {@code Map.get} returns null on a miss; the hidden
 * auto-unboxing calls {@code null.intValue()} — NullPointerException on exactly the miss
 * path, the one tests rarely exercise. The stack trace points at an assignment with no
 * visible dereference anywhere.
 *
 * <p><b>How to fix it:</b> {@code getOrDefault} with a real default, as
 * {@link #fixed(Map, String)} does (or the boxed type plus an explicit null check).
 */
public class UnboxNpeExample {

  public int buggy(Map<String, Integer> scores, String player) {
    int score = scores.get(player); // :: CK-UNBOX-NPE
    return score * 2;
  }

  public int fixed(Map<String, Integer> scores, String player) {
    int score = scores.getOrDefault(player, 0);
    return score * 2;
  }
}
