package io.codekoll.rules.apimisuse;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ApiMisuseBatch2RulesTest {

  @Test
  void immutableMutateFlagged() {
    RuleTestHarness.assertFixture(new ImmutableMutateRule(), "P1", """
        import java.util.List;
        class P1 {
          void m() {
            List<String> roles = List.of("admin");
            roles.add("user"); // :: CK-IMMUTABLE-MUTATE
          }
        }
        """);
  }

  @Test
  void mutableCopyAllowed() {
    RuleTestHarness.assertFixture(new ImmutableMutateRule(), "N1", """
        import java.util.ArrayList;
        import java.util.List;
        class N1 {
          void m() {
            List<String> roles = new ArrayList<>(List.of("admin"));
            roles.add("user");
          }
        }
        """);
  }

  @Test
  void localeCaseFlagged() {
    RuleTestHarness.assertFixture(new LocaleCaseRule(), "P2", """
        class P2 {
          String m(String s) {
            return s.toLowerCase(); // :: CK-LOCALE-CASE
          }
        }
        """);
  }

  @Test
  void localeRootAllowed() {
    RuleTestHarness.assertFixture(new LocaleCaseRule(), "N2", """
        import java.util.Locale;
        class N2 {
          String m(String s) {
            return s.toLowerCase(Locale.ROOT);
          }
        }
        """);
  }

  @Test
  void computeIfAbsentModFlagged() {
    RuleTestHarness.assertFixture(new ComputeIfAbsentModRule(), "P3", """
        import java.util.Map;
        class P3 {
          void m(Map<String, String> cache, String key) {
            cache.computeIfAbsent(key, k -> {
              cache.put("other", "value"); // :: CK-COMPUTE-IF-ABSENT-MOD
              return k.toUpperCase(java.util.Locale.ROOT);
            });
          }
        }
        """);
  }

  @Test
  void pureComputeLambdaAllowed() {
    RuleTestHarness.assertFixture(new ComputeIfAbsentModRule(), "N3", """
        import java.util.Map;
        class N3 {
          void m(Map<String, Integer> cache, String key) {
            cache.computeIfAbsent(key, k -> k.length());
          }
        }
        """);
  }
}
