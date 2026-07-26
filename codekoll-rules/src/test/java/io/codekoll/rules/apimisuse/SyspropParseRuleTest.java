package io.codekoll.rules.apimisuse;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class SyspropParseRuleTest {

  private final SyspropParseRule rule = new SyspropParseRule();

  @Test
  void flagsBooleanGetBooleanOnAComputedString() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.util.Properties;
        class P1 {
          boolean m(Properties config) {
            String enabled = config.getProperty("feature.enabled");
            return Boolean.getBoolean(enabled); // :: CK-SYSPROP-PARSE
          }
        }
        """);
  }

  @Test
  void flagsBooleanGetBooleanOnALiteralValue() {
    RuleTestHarness.assertFixture(rule, "P2", """
        class P2 {
          boolean m() {
            return Boolean.getBoolean("true"); // :: CK-SYSPROP-PARSE
          }
        }
        """);
  }

  @Test
  void flagsIntegerGetIntegerOnAComputedString() {
    RuleTestHarness.assertFixture(rule, "P3", """
        class P3 {
          int m(String raw) {
            return Integer.getInteger(raw); // :: CK-SYSPROP-PARSE
          }
        }
        """);
  }

  @Test
  void flagsIntegerGetIntegerOnANumericLiteral() {
    RuleTestHarness.assertFixture(rule, "P4", """
        class P4 {
          Integer m() {
            return Integer.getInteger("8080"); // :: CK-SYSPROP-PARSE
          }
        }
        """);
  }

  @Test
  void flagsLongGetLongOnAComputedString() {
    RuleTestHarness.assertFixture(rule, "P5", """
        class P5 {
          Long m(String raw) {
            return Long.getLong(raw.trim()); // :: CK-SYSPROP-PARSE
          }
        }
        """);
  }

  @Test
  void allowsGenuinePropertyLookupByDottedLiteral() {
    RuleTestHarness.assertFixture(rule, "N1", """
        class N1 {
          boolean m() {
            return Boolean.getBoolean("acme.tracing.enabled");
          }
        }
        """);
  }

  @Test
  void allowsGenuinePropertyLookupByDottedConstant() {
    RuleTestHarness.assertFixture(rule, "N2", """
        class N2 {
          private static final String DEBUG_PROPERTY = "acme.debug";
          boolean m() {
            return Boolean.getBoolean(DEBUG_PROPERTY);
          }
        }
        """);
  }

  @Test
  void allowsPropertyNameBuiltFromADottedPrefix() {
    RuleTestHarness.assertFixture(rule, "N3", """
        class N3 {
          Integer m(String module) {
            return Integer.getInteger("acme.pool." + module + ".size");
          }
        }
        """);
  }

  @Test
  void allowsUndottedConstantPropertyName() {
    RuleTestHarness.assertFixture(rule, "N4", """
        class N4 {
          Long m() {
            return Long.getLong("timeout");
          }
        }
        """);
  }

  @Test
  void allowsTheRealParsingMethods() {
    RuleTestHarness.assertFixture(rule, "N5", """
        class N5 {
          boolean flag(String raw) {
            return Boolean.parseBoolean(raw);
          }
          int port(String raw) {
            return Integer.parseInt(raw);
          }
          long millis(String raw) {
            return Long.parseLong(raw);
          }
        }
        """);
  }

  @Test
  void allowsSameNamedMethodOnAnUnrelatedType() {
    RuleTestHarness.assertFixture(rule, "N6", """
        class N6 {
          static final class Settings {
            boolean getBoolean(String value) {
              return "true".equals(value);
            }
          }
          boolean m(Settings settings, String raw) {
            return settings.getBoolean(raw);
          }
        }
        """);
  }
}
