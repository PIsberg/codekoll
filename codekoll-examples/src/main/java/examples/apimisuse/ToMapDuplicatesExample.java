package examples.apimisuse;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Example for rule {@code CK-TOMAP-DUPLICATES}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(List)} indexes users by email with two-argument
 * {@code Collectors.toMap} — no merge function.
 *
 * <p><b>What happens at runtime:</b> the first time two entries share an email,
 * {@code IllegalStateException: Duplicate key}. Test data rarely has duplicates; production
 * data eventually does — the pipeline that ran clean for months crashes the day it matters.
 *
 * <p><b>How to fix it:</b> state the merge policy, as {@link #fixed(List)} does (or use
 * {@code groupingBy} when duplicates are expected data).
 */
public class ToMapDuplicatesExample {

  public Map<String, String> buggy(List<String> emails) {
    return emails.stream()
        .collect(Collectors.toMap(String::toLowerCase, Function.identity())); // :: CK-TOMAP-DUPLICATES
  }

  public Map<String, String> fixed(List<String> emails) {
    return emails.stream()
        .collect(Collectors.toMap(String::toLowerCase, Function.identity(), (first, dup) -> first));
  }
}
