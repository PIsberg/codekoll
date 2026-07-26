package io.codekoll.rules.apimisuse;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ImmutableFactoryNullRuleTest {

  private final ImmutableFactoryNullRule rule = new ImmutableFactoryNullRule();

  @Test
  void flagsNullElementInListOf() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.util.List;
        class P1 {
          List<String> m(String middleName) {
            return List.of("Ada", null, "Lovelace"); // :: CK-IMMUTABLE-FACTORY-NULL
          }
        }
        """);
  }

  @Test
  void flagsNullElementInSetOf() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import java.util.Set;
        class P2 {
          Set<String> m() {
            return Set.of(null); // :: CK-IMMUTABLE-FACTORY-NULL
          }
        }
        """);
  }

  @Test
  void flagsNullValueInMapOf() {
    RuleTestHarness.assertFixture(rule, "P3", """
        import java.util.Map;
        class P3 {
          Map<String, String> m() {
            return Map.of("region", "eu-west", "zone", null); // :: CK-IMMUTABLE-FACTORY-NULL
          }
        }
        """);
  }

  @Test
  void flagsNullInMapEntry() {
    RuleTestHarness.assertFixture(rule, "P4", """
        import java.util.Map;
        class P4 {
          Map.Entry<String, String> m() {
            return Map.entry("zone", null); // :: CK-IMMUTABLE-FACTORY-NULL
          }
        }
        """);
  }

  @Test
  void flagsCastNullElement() {
    RuleTestHarness.assertFixture(rule, "P5", """
        import java.util.List;
        class P5 {
          List<String> m() {
            return List.of("a", (String) null); // :: CK-IMMUTABLE-FACTORY-NULL
          }
        }
        """);
  }

  @Test
  void flagsNullArgumentToCopyOf() {
    RuleTestHarness.assertFixture(rule, "P6", """
        import java.util.List;
        class P6 {
          List<String> m() {
            return List.copyOf(null); // :: CK-IMMUTABLE-FACTORY-NULL
          }
        }
        """);
  }

  @Test
  void allowsNullFreeFactoryCalls() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.util.List;
        import java.util.Map;
        import java.util.Set;
        class N1 {
          void m() {
            List<String> a = List.of("x", "y");
            Set<String> b = Set.of("x");
            Map<String, Integer> c = Map.of("x", 1);
          }
        }
        """);
  }

  @Test
  void allowsNullInFactoriesThatPermitIt() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import java.util.Arrays;
        import java.util.Collections;
        import java.util.List;
        class N2 {
          void m() {
            List<String> a = Arrays.asList("x", null);
            List<String> b = Collections.singletonList(null);
          }
        }
        """);
  }

  @Test
  void allowsNullInMutableCollections() {
    RuleTestHarness.assertFixture(rule, "N3", """
        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.List;
        import java.util.Map;
        class N3 {
          void m() {
            List<String> a = new ArrayList<>();
            a.add(null);
            Map<String, String> b = new HashMap<>();
            b.put("zone", null);
          }
        }
        """);
  }

  @Test
  void allowsSameNamedFactoryOnAnUnrelatedType() {
    RuleTestHarness.assertFixture(rule, "N4", """
        class N4 {
          static final class Box {
            static Box of(String value) {
              return new Box();
            }
          }
          Box m() {
            return Box.of(null);
          }
        }
        """);
  }

  @Test
  void allowsNullReturnedFromAMethodPassedToListOf() {
    RuleTestHarness.assertFixture(rule, "N5", """
        import java.util.List;
        class N5 {
          private String lookup() {
            return null;
          }
          List<String> m() {
            return List.of("a", lookup());
          }
        }
        """);
  }
}
