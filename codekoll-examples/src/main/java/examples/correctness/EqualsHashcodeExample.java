package examples.correctness;

import java.util.Objects;

/**
 * Example for rule {@code CK-EQUALS-HASHCODE}.
 *
 * <p><b>What is wrong:</b> the {@code buggy} class overrides {@code equals} but not
 * {@code hashCode}.
 *
 * <p><b>What happens at runtime:</b> HashMap and HashSet find objects by hashCode FIRST.
 * Two equal instances get different (identity) hash codes, land in different buckets, and
 * {@code set.contains(equalCopy)} returns false — duplicates accumulate, cache lookups
 * miss. Tests using the same instance pass; real data fails.
 *
 * <p><b>How to fix it:</b> override both from the same fields, as {@code fixed} does — or
 * use a record.
 */
public class EqualsHashcodeExample {

  static class buggy { // :: CK-EQUALS-HASHCODE
    final String sku;

    buggy(String sku) {
      this.sku = sku;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof buggy other && sku.equals(other.sku);
    }
  }

  static class fixed {
    final String sku;

    fixed(String sku) {
      this.sku = sku;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof fixed other && sku.equals(other.sku);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sku);
    }
  }
}
