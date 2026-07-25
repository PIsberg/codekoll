package io.codekoll.rules.security;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class SecurityBatch3RulesTest {

  @Test
  void xxeFactoryFlagged() {
    RuleTestHarness.assertFixture(new XxeFactoryRule(), "P1", """
        import javax.xml.parsers.DocumentBuilderFactory;
        class P1 {
          DocumentBuilderFactory m() {
            return DocumentBuilderFactory.newInstance(); // :: CK-XXE-FACTORY
          }
        }
        """);
  }

  @Test
  void hardenedFactoryAllowed() {
    RuleTestHarness.assertFixture(new XxeFactoryRule(), "N1", """
        import javax.xml.parsers.DocumentBuilderFactory;
        import javax.xml.parsers.ParserConfigurationException;
        class N1 {
          DocumentBuilderFactory m() throws ParserConfigurationException {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory;
          }
        }
        """);
  }

  @Test
  void redosFlagged() {
    RuleTestHarness.assertFixture(new RedosRule(), "P2", """
        import java.util.regex.Pattern;
        class P2 {
          Pattern m() {
            return Pattern.compile("(a+)+$"); // :: CK-REDOS
          }
          boolean n(String s) {
            return s.matches("(x|xx)+"); // :: CK-REDOS
          }
        }
        """);
  }

  @Test
  void safeRegexAllowed() {
    RuleTestHarness.assertFixture(new RedosRule(), "N2", """
        import java.util.regex.Pattern;
        class N2 {
          Pattern m() {
            return Pattern.compile("[a-z]+@[a-z]+\\\\.[a-z]+");
          }
          boolean n(String s) {
            return s.matches("\\\\d{3}-\\\\d{4}");
          }
        }
        """);
  }
}
