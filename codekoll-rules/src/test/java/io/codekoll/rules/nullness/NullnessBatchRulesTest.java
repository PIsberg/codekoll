package io.codekoll.rules.nullness;

import io.codekoll.engine.testing.RuleTestHarness;
import io.codekoll.rules.resources.SystemExitRule;
import org.junit.jupiter.api.Test;

class NullnessBatchRulesTest {

  @Test
  void nonShortCircuitFlagged() {
    RuleTestHarness.assertFixture(new NonShortCircuitRule(), "P1", """
        class P1 {
          boolean m(String x) {
            return x != null & x.length() > 0; // :: CK-NON-SHORT-CIRCUIT
          }
        }
        """);
  }

  @Test
  void shortCircuitAndBitwiseIntAllowed() {
    RuleTestHarness.assertFixture(new NonShortCircuitRule(), "N1", """
        class N1 {
          boolean m(String x, int flags) {
            boolean guarded = x != null && x.length() > 0;
            int masked = flags & 0xFF;
            boolean unrelated = x != null & flags > 0;
            return guarded || masked > 0 || unrelated;
          }
        }
        """);
  }

  @Test
  void unboxNpeFlagged() {
    RuleTestHarness.assertFixture(new UnboxNpeRule(), "P2", """
        import java.util.Map;
        class P2 {
          int m(Map<String, Integer> scores, String key) {
            int score = scores.get(key); // :: CK-UNBOX-NPE
            return score;
          }
        }
        """);
  }

  @Test
  void boxedAssignmentAndGetOrDefaultAllowed() {
    RuleTestHarness.assertFixture(new UnboxNpeRule(), "N2", """
        import java.util.Map;
        class N2 {
          int m(Map<String, Integer> scores, String key) {
            Integer boxed = scores.get(key);
            int withDefault = scores.getOrDefault(key, 0);
            return (boxed == null ? 0 : boxed) + withDefault;
          }
        }
        """);
  }

  @Test
  void optionalGetBareFlagged() {
    RuleTestHarness.assertFixture(new OptionalGetBareRule(), "P3", """
        import java.util.Optional;
        class P3 {
          String m() {
            return find("x").get(); // :: CK-OPTIONAL-GET-BARE
          }
          Optional<String> find(String id) {
            return Optional.of(id);
          }
        }
        """);
  }

  @Test
  void handledAbsenceAllowed() {
    RuleTestHarness.assertFixture(new OptionalGetBareRule(), "N3", """
        import java.util.Optional;
        class N3 {
          String m() {
            String a = find("x").orElse("fallback");
            String b = find("y").orElseThrow(() -> new IllegalStateException("missing"));
            Optional<String> held = find("z");
            String c = held.isPresent() ? held.get() : "";
            return a + b + c;
          }
          Optional<String> find(String id) {
            return Optional.of(id);
          }
        }
        """);
  }

  @Test
  void systemExitInLibraryCodeFlagged() {
    RuleTestHarness.assertFixture(new SystemExitRule(), "P4", """
        class P4Service {
          void m(boolean bad) {
            if (bad) {
              System.exit(1); // :: CK-SYSTEM-EXIT
            }
          }
        }
        """);
  }

  @Test
  void exitInMainAndLauncherAllowed() {
    RuleTestHarness.assertFixture(new SystemExitRule(), "N4", """
        class N4Main {
          public static void main(String[] args) {
            System.exit(run(args));
          }
          static int run(String[] args) {
            return args.length;
          }
        }
        """);
  }
}
