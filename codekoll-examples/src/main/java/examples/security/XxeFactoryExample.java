package examples.security;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Example for rule {@code CK-XXE-FACTORY}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} creates a {@code DocumentBuilderFactory} and
 * returns it without any hardening.
 *
 * <p><b>What happens at runtime:</b> the default factory resolves external entities and
 * DOCTYPE declarations. A hostile document can read local files
 * ({@code <!ENTITY x SYSTEM "file:///etc/passwd">}), reach internal URLs (SSRF), or blow up
 * memory (billion laughs) — XML External Entity injection, a routine finding on any parser
 * left at defaults.
 *
 * <p><b>How to fix it:</b> disable DOCTYPE before parsing, as {@link #fixed()} does.
 */
public class XxeFactoryExample {

  public DocumentBuilderFactory buggy() {
    return DocumentBuilderFactory.newInstance(); // :: CK-XXE-FACTORY
  }

  public DocumentBuilderFactory fixed() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setExpandEntityReferences(false);
    return factory;
  }
}
