package io.codekoll.rules.apimisuse;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ImmutableFactoryDuplicateRuleTest {

  private final ImmutableFactoryDuplicateRule rule = new ImmutableFactoryDuplicateRule();

  @Test
  void flagsDuplicateStringElementInSetOf() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.util.Set;
        class P1 {
          Set<String> m() {
            return Set.of("read", "write", "read"); // :: CK-IMMUTABLE-FACTORY-DUPLICATE
          }
        }
        """);
  }

  @Test
  void flagsDuplicateKeyInMapOf() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import java.util.Map;
        class P2 {
          Map<String, Integer> m() {
            return Map.of("eu", 1, "us", 2, "eu", 3); // :: CK-IMMUTABLE-FACTORY-DUPLICATE
          }
        }
        """);
  }

  @Test
  void flagsDuplicateEnumConstantInSetOf() {
    RuleTestHarness.assertFixture(rule, "P3", """
        import java.util.Set;
        class P3 {
          enum Role { READER, WRITER }
          Set<Role> m() {
            return Set.of(Role.READER, Role.WRITER, Role.READER); // :: CK-IMMUTABLE-FACTORY-DUPLICATE
          }
        }
        """);
  }

  @Test
  void flagsDuplicateConstantFieldElement() {
    RuleTestHarness.assertFixture(rule, "P4", """
        import java.util.Set;
        class P4 {
          private static final String ADMIN = "admin";
          Set<String> m() {
            return Set.of(ADMIN, "guest", "admin"); // :: CK-IMMUTABLE-FACTORY-DUPLICATE
          }
        }
        """);
  }

  @Test
  void flagsDuplicateKeyInMapOfEntries() {
    RuleTestHarness.assertFixture(rule, "P5", """
        import java.util.Map;
        class P5 {
          Map<String, Integer> m() {
            return Map.ofEntries(
                Map.entry("eu", 1),
                Map.entry("us", 2),
                Map.entry("eu", 3)); // :: CK-IMMUTABLE-FACTORY-DUPLICATE
          }
        }
        """);
  }

  @Test
  void flagsDuplicateIntegerElement() {
    RuleTestHarness.assertFixture(rule, "P6", """
        import java.util.Set;
        class P6 {
          Set<Integer> m() {
            return Set.of(1, 2, 2); // :: CK-IMMUTABLE-FACTORY-DUPLICATE
          }
        }
        """);
  }

  @Test
  void allowsDistinctElementsAndKeys() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.util.Map;
        import java.util.Set;
        class N1 {
          void m() {
            Set<String> a = Set.of("read", "write");
            Map<String, Integer> b = Map.of("eu", 1, "us", 2);
          }
        }
        """);
  }

  @Test
  void allowsRepeatedValuesUnderDistinctKeys() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import java.util.Map;
        class N2 {
          Map<String, Integer> m() {
            return Map.of("eu", 1, "us", 1, "ap", 1);
          }
        }
        """);
  }

  @Test
  void allowsDuplicatesInListOfWhichPermitsThem() {
    RuleTestHarness.assertFixture(rule, "N3", """
        import java.util.List;
        class N3 {
          List<String> m() {
            return List.of("read", "write", "read");
          }
        }
        """);
  }

  @Test
  void allowsNonConstantElementsThatMayCoincide() {
    RuleTestHarness.assertFixture(rule, "N4", """
        import java.util.Set;
        class N4 {
          Set<String> m(String a, String b) {
            return Set.of(a, b);
          }
        }
        """);
  }

  @Test
  void allowsDuplicatesInAMutableCollection() {
    RuleTestHarness.assertFixture(rule, "N5", """
        import java.util.HashSet;
        import java.util.Set;
        class N5 {
          Set<String> m() {
            Set<String> roles = new HashSet<>();
            roles.add("read");
            roles.add("read");
            return roles;
          }
        }
        """);
  }

  @Test
  void allowsSameNamedFactoryOnAnUnrelatedType() {
    RuleTestHarness.assertFixture(rule, "N6", """
        class N6 {
          static final class Tuple {
            static Tuple of(String a, String b) {
              return new Tuple();
            }
          }
          Tuple m() {
            return Tuple.of("x", "x");
          }
        }
        """);
  }
}
