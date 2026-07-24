package io.codekoll.api;

/**
 * Stable identifier of a rule, e.g. {@code CK-EMPTY-CATCH}. Rule ids are part of the public
 * contract (they appear in user suppressions) and never change once released.
 */
public record RuleId(String value) {

  public RuleId {
    // Validated without a regex: linear, and immune to the ReDoS shape find-sec-bugs
    // (correctly) refuses to reason about.
    if (!isValid(value)) {
      throw new IllegalArgumentException("Rule id must match CK-SCREAMING-KEBAB: " + value);
    }
  }

  private static boolean isValid(String value) {
    if (!value.startsWith("CK-") || value.endsWith("-")) {
      return false;
    }
    boolean previousWasDash = false;
    for (int i = "CK-".length(); i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '-') {
        if (previousWasDash) {
          return false;
        }
        previousWasDash = true;
      } else if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
        previousWasDash = false;
      } else {
        return false;
      }
    }
    return value.length() > "CK-".length();
  }

  @Override
  public String toString() {
    return value;
  }
}
