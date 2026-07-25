package examples.apimisuse;

import java.util.ArrayList;
import java.util.List;

/**
 * Example for rule {@code CK-IMMUTABLE-MUTATE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} builds a list with {@code List.of(...)} and
 * then tries to {@code add} to it.
 *
 * <p><b>What happens at runtime:</b> {@code List.of} returns an immutable list; {@code add}
 * throws {@code UnsupportedOperationException}. It compiles because the static type is
 * {@code List}, and fails on the first modification — often only the code path that adds an
 * element, so it slips past shallow tests.
 *
 * <p><b>How to fix it:</b> start from a mutable copy, as {@link #fixed()} does.
 */
public class ImmutableMutateExample {

  public List<String> buggy() {
    List<String> roles = List.of("admin");
    roles.add("auditor"); // :: CK-IMMUTABLE-MUTATE
    return roles;
  }

  public List<String> fixed() {
    List<String> roles = new ArrayList<>(List.of("admin"));
    roles.add("auditor");
    return roles;
  }
}
