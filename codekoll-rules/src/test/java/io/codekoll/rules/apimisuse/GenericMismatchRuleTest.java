package io.codekoll.rules.apimisuse;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class GenericMismatchRuleTest {

  private final GenericMismatchRule rule = new GenericMismatchRule();

  @Test
  void flagsIntegerKeyOnStringMap() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.util.HashMap;
        import java.util.Map;
        class P1 {
          Object m() {
            Map<String, Object> cache = new HashMap<>();
            return cache.get(12345); // :: CK-GENERIC-MISMATCH
          }
        }
        """);
  }

  @Test
  void flagsIntLiteralOnLongKeyedMap() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import java.util.HashMap;
        import java.util.Map;
        class P2 {
          Object m() {
            Map<Long, Object> byId = new HashMap<>();
            return byId.get(42); // :: CK-GENERIC-MISMATCH
          }
        }
        """);
  }

  @Test
  void flagsWrongTypeInListContains() {
    RuleTestHarness.assertFixture(rule, "P3", """
        import java.util.List;
        class P3 {
          boolean m(List<String> names) {
            return names.contains(42); // :: CK-GENERIC-MISMATCH
          }
        }
        """);
  }

  @Test
  void flagsContainsValueMismatch() {
    RuleTestHarness.assertFixture(rule, "P4", """
        import java.util.Map;
        class P4 {
          boolean m(Map<String, Integer> scores) {
            return scores.containsValue("high"); // :: CK-GENERIC-MISMATCH
          }
        }
        """);
  }

  @Test
  void allowsCorrectKeyType() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.util.Map;
        class N1 {
          Object m(Map<String, Object> cache, String key) {
            return cache.get(key);
          }
        }
        """);
  }

  @Test
  void allowsSubtypeAndSupertypeArguments() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import java.util.List;
        class N2 {
          boolean m(List<CharSequence> seqs, String s, Object o) {
            return seqs.contains(s) || seqs.contains(o);
          }
        }
        """);
  }

  @Test
  void allowsRawAndWildcardReceivers() {
    RuleTestHarness.assertFixture(rule, "N3", """
        import java.util.List;
        import java.util.Map;
        class N3 {
          @SuppressWarnings({"rawtypes", "unchecked"})
          boolean m(Map raw, List<?> wildcard) {
            return raw.get(42) != null || wildcard.contains("x");
          }
        }
        """);
  }

  @Test
  void allowsListRemoveByIndex() {
    RuleTestHarness.assertFixture(rule, "N4", """
        import java.util.List;
        class N4 {
          String m(List<String> names) {
            return names.remove(0);
          }
        }
        """);
  }
}
