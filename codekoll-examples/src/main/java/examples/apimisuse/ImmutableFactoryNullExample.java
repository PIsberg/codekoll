package examples.apimisuse;

import java.util.Map;

/**
 * Example for rule {@code CK-IMMUTABLE-FACTORY-NULL}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} builds a defaults map with {@code Map.of} and uses
 * {@code null} to mean "not configured". The Java 9 immutable-collection factories
 * ({@code List.of}, {@code Set.of}, {@code Map.of}, {@code Map.entry}, {@code copyOf}) reject
 * null keys, values and elements by contract.
 *
 * <p><b>What happens at runtime:</b> the call throws {@code NullPointerException} before the
 * map is ever built — every single time, on the first execution of that line. Nothing in the
 * type system hints at it, so it compiles cleanly. When such a map is a {@code static final}
 * field, the NPE surfaces as {@code ExceptionInInitializerError}, and every later attempt to
 * touch the class reports {@code NoClassDefFoundError} instead — an error that names a class
 * which looks entirely unrelated to the missing config value.
 *
 * <p><b>How to fix it:</b> absent means absent — leave the key out, as {@link #fixed()} does,
 * and let callers use {@code getOrDefault}. If you genuinely need to store null, use a
 * collection that permits it: {@code Arrays.asList(...)}, {@code new ArrayList<>()},
 * {@code new HashMap<>()}.
 */
public class ImmutableFactoryNullExample {

  public Map<String, String> buggy() {
    return Map.of(
        "region", "eu-west-1",
        "endpointOverride", null); // :: CK-IMMUTABLE-FACTORY-NULL
  }

  public Map<String, String> fixed() {
    return Map.of("region", "eu-west-1");
  }
}
