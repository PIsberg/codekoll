package examples.apimisuse;

import java.util.Map;

/**
 * Example for rule {@code CK-IMMUTABLE-FACTORY-DUPLICATE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} maps region codes to shard numbers with
 * {@code Map.of}, and {@code "eu-west-1"} appears twice — the kind of repeat that hides in a
 * long literal table and survives review because the eye reads the values, not the keys.
 *
 * <p><b>What happens at runtime:</b> {@code Map.of} refuses duplicate keys, and {@code Set.of}
 * refuses duplicate elements: the call throws
 * {@code IllegalArgumentException: duplicate key: eu-west-1} while the map is being built.
 * The habit comes from {@code HashMap.put}, which silently keeps the last value — so the same
 * copy-paste used to be invisible. Tables like this are usually {@code static final} fields,
 * which means the failure arrives as {@code ExceptionInInitializerError} during class loading,
 * before any code that reads the duplicated key ever runs.
 *
 * <p><b>How to fix it:</b> remove the repeat, as {@link #fixed()} does. If both entries are
 * genuinely wanted and the last one should win, build the map with {@code new HashMap<>()}
 * instead — that is the collection whose contract allows it.
 */
public class ImmutableFactoryDuplicateExample {

  public Map<String, Integer> buggy() {
    return Map.of(
        "eu-west-1", 1,
        "us-east-1", 2,
        "eu-west-1", 3); // :: CK-IMMUTABLE-FACTORY-DUPLICATE
  }

  public Map<String, Integer> fixed() {
    return Map.of(
        "eu-west-1", 1,
        "us-east-1", 2,
        "ap-south-1", 3);
  }
}
