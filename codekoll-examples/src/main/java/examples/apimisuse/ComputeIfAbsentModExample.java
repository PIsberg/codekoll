package examples.apimisuse;

import java.util.HashMap;
import java.util.Map;

/**
 * Example for rule {@code CK-COMPUTE-IF-ABSENT-MOD}.
 *
 * <p><b>What is wrong:</b> the {@code computeIfAbsent} lambda in {@link #buggy(String)}
 * calls {@code put} on the same map it is populating.
 *
 * <p><b>What happens at runtime:</b> the mapping function runs mid-operation, while the map
 * is in an inconsistent internal state. Modifying it there throws
 * {@code ConcurrentModificationException} on HashMap (since JDK 9) — a nested cache-warming
 * bug that reads as perfectly reasonable code.
 *
 * <p><b>How to fix it:</b> compute the value without touching the map, then do any extra
 * updates after {@code computeIfAbsent} returns, as {@link #fixed(String)} does.
 */
public class ComputeIfAbsentModExample {

  private final Map<String, Integer> cache = new HashMap<>();

  public int buggy(String key) {
    return cache.computeIfAbsent(key, k -> {
      cache.put(k + ".meta", 0); // :: CK-COMPUTE-IF-ABSENT-MOD
      return k.length();
    });
  }

  public int fixed(String key) {
    int value = cache.computeIfAbsent(key, String::length);
    cache.putIfAbsent(key + ".meta", 0);
    return value;
  }
}
