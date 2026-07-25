package examples.performance;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Example for rule {@code CK-CONTAINS-IN-LOOP}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(List, List)} calls {@code blocked.contains(id)}
 * for every element of {@code candidates}.
 *
 * <p><b>What happens at runtime:</b> {@code List.contains} scans the whole list — O(n) — so
 * the loop is O(n*m). On the small lists in tests it is instant; on production-sized inputs
 * it is quadratic and the request that took milliseconds takes minutes.
 *
 * <p><b>How to fix it:</b> build a {@code HashSet} once before the loop for O(1) lookups,
 * as {@link #fixed(List, List)} does.
 */
public class ContainsInLoopExample {

  public int buggy(List<String> candidates, List<String> blocked) {
    int allowed = 0;
    for (String id : candidates) {
      if (!blocked.contains(id)) { // :: CK-CONTAINS-IN-LOOP
        allowed++;
      }
    }
    return allowed;
  }

  public int fixed(List<String> candidates, List<String> blocked) {
    Set<String> blockedSet = new HashSet<>(blocked);
    int allowed = 0;
    for (String id : candidates) {
      if (!blockedSet.contains(id)) {
        allowed++;
      }
    }
    return allowed;
  }
}
