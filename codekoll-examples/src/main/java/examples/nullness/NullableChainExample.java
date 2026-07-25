package examples.nullness;

import java.util.Map;

/**
 * Example for rule {@code CK-NULLABLE-CHAIN}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Map, String)} chains {@code .trim()} directly onto
 * {@code config.get(key)}.
 *
 * <p><b>What happens at runtime:</b> {@code Map.get} returns null when the key is absent,
 * so the chained {@code .trim()} throws {@code NullPointerException} — on exactly the
 * missing-config path, the one integration tests rarely cover.
 *
 * <p><b>How to fix it:</b> store the result and handle absence (here with
 * {@code getOrDefault}), as {@link #fixed(Map, String)} does.
 */
public class NullableChainExample {

  public String buggy(Map<String, String> config, String key) {
    return config.get(key).trim(); // :: CK-NULLABLE-CHAIN
  }

  public String fixed(Map<String, String> config, String key) {
    return config.getOrDefault(key, "").trim();
  }
}
