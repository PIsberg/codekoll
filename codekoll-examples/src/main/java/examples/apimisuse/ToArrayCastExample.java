package examples.apimisuse;

import java.util.List;

/**
 * Example for rule {@code CK-TOARRAY-CAST}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(List)} casts the result of no-arg {@code toArray()}
 * to {@code String[]}.
 *
 * <p><b>What happens at runtime:</b> {@code toArray()} allocates an {@code Object[]} — its
 * runtime class is Object[] no matter what it contains — so the cast throws
 * {@code ClassCastException} on every single call. This path has never once worked.
 *
 * <p><b>How to fix it:</b> the typed overload, as {@link #fixed(List)} does.
 */
public class ToArrayCastExample {

  public String[] buggy(List<String> names) {
    return (String[]) names.toArray(); // :: CK-TOARRAY-CAST
  }

  public String[] fixed(List<String> names) {
    return names.toArray(new String[0]);
  }
}
