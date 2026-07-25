package examples.performance;

import java.util.Map;

/**
 * Example for rule {@code CK-KEYSET-GET}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Map)} iterates {@code keySet()} and calls
 * {@code get(key)} for each key.
 *
 * <p><b>What happens at runtime:</b> the loop is already standing on every entry, then pays
 * a full hash lookup (hash, bucket probe, equals) to re-find the value it just walked past —
 * doubling map access cost for the entire traversal.
 *
 * <p><b>How to fix it:</b> iterate {@code entrySet()}, which delivers key and value
 * together, as {@link #fixed(Map)} does.
 */
public class KeysetGetExample {

  public long buggy(Map<String, Long> balances) {
    long total = 0;
    for (String account : balances.keySet()) {
      total += balances.get(account); // :: CK-KEYSET-GET
    }
    return total;
  }

  public long fixed(Map<String, Long> balances) {
    long total = 0;
    for (Map.Entry<String, Long> entry : balances.entrySet()) {
      total += entry.getValue();
    }
    return total;
  }
}
