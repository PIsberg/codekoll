package io.codekoll.rules.nullness;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class NullnessBatch2RulesTest {

  @Test
  void nullableChainFlagged() {
    RuleTestHarness.assertFixture(new NullableChainRule(), "P1", """
        import java.util.Map;
        class P1 {
          int m(Map<String, String> cache, String key) {
            return cache.get(key).length(); // :: CK-NULLABLE-CHAIN
          }
        }
        """);
  }

  @Test
  void nullCheckedResultAllowed() {
    RuleTestHarness.assertFixture(new NullableChainRule(), "N1", """
        import java.util.Map;
        class N1 {
          int m(Map<String, String> cache, String key) {
            String value = cache.get(key);
            return value == null ? 0 : value.length();
          }
        }
        """);
  }

  @Test
  void optionalOfNullableFlagged() {
    RuleTestHarness.assertFixture(new OptionalOfNullableRule(), "P2", """
        import java.util.Map;
        import java.util.Optional;
        class P2 {
          Optional<String> m(Map<String, String> cache, String key) {
            return Optional.of(cache.get(key)); // :: CK-OPTIONAL-OF-NULLABLE
          }
        }
        """);
  }

  @Test
  void ofNullableAndKnownPresentAllowed() {
    RuleTestHarness.assertFixture(new OptionalOfNullableRule(), "N2", """
        import java.util.Map;
        import java.util.Optional;
        class N2 {
          Optional<String> m(Map<String, String> cache, String key) {
            return Optional.ofNullable(cache.get(key));
          }
          Optional<String> known() {
            return Optional.of("always here");
          }
        }
        """);
  }

  @Test
  void nullToNonNullFlagged() {
    RuleTestHarness.assertFixture(new NullToNonnullRule(), "P3", """
        import org.jspecify.annotations.NonNull;
        class P3 {
          void sink(@NonNull String value) {
          }
          void m() {
            sink(null); // :: CK-NULL-TO-NONNULL
          }
        }
        """);
  }

  @Test
  void nonNullArgumentAllowed() {
    RuleTestHarness.assertFixture(new NullToNonnullRule(), "N3", """
        import org.jspecify.annotations.NonNull;
        import org.jspecify.annotations.Nullable;
        class N3 {
          void sink(@NonNull String value) {
          }
          void nullableSink(@Nullable String value) {
          }
          void m() {
            sink("ok");
            nullableSink(null);
          }
        }
        """);
  }

  @Test
  void overrideNullnessFlagged() {
    RuleTestHarness.assertFixture(new OverrideNullnessRule(), "P4", """
        import org.jspecify.annotations.NonNull;
        import org.jspecify.annotations.Nullable;
        class P4 {
          interface Repo {
            @NonNull String find(String id);
          }
          static class Impl implements Repo {
            @Override // :: CK-OVERRIDE-NULLNESS
            public @Nullable String find(String id) {
              return null;
            }
          }
        }
        """);
  }

  @Test
  void contractPreservingOverrideAllowed() {
    RuleTestHarness.assertFixture(new OverrideNullnessRule(), "N4", """
        import org.jspecify.annotations.NonNull;
        class N4 {
          interface Repo {
            @NonNull String find(String id);
          }
          static class Impl implements Repo {
            @Override
            public @NonNull String find(String id) {
              return "found";
            }
          }
        }
        """);
  }
}
