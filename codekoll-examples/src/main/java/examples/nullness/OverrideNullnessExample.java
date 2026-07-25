package examples.nullness;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Example for rule {@code CK-OVERRIDE-NULLNESS}.
 *
 * <p><b>What is wrong:</b> {@code buggy}'s {@code find} override returns {@code @Nullable}
 * where the interface it implements promises {@code @NonNull}.
 *
 * <p><b>What happens at runtime:</b> code holding a {@code Repository} reference trusts the
 * interface's {@code @NonNull} contract and does not null-check the result. Dispatched to
 * this implementation it receives null anyway and throws {@code NullPointerException} — the
 * compiler verified the override signature but not its nullness.
 *
 * <p><b>How to fix it:</b> keep the override's return at least as strict as the supertype's,
 * as {@code Fixed} does.
 */
public class OverrideNullnessExample {

  interface Repository {
    @NonNull String find(String id);
  }

  static class buggy implements Repository {
    @Override // :: CK-OVERRIDE-NULLNESS
    public @Nullable String find(String id) {
      return id.isEmpty() ? null : id;
    }
  }

  static class Fixed implements Repository {
    @Override
    public @NonNull String find(String id) {
      return id.isEmpty() ? "unknown" : id;
    }

    String fixed(String id) {
      return find(id);
    }
  }
}
