package io.codekoll.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads the handful of facts codekoll needs out of a {@code pom.xml}: the language level and the
 * declared modules. It does not resolve the effective POM — that would mean running Maven.
 *
 * <p>The parser is hardened against XXE (no DTDs, no external entities, secure processing). A
 * static analyzer that ships a rule for XML parser hardening has no excuse for parsing a
 * stranger's build file carelessly.
 */
final class PomReader {

  private final Map<String, String> properties = new HashMap<>();
  private final List<String> modules = new ArrayList<>();
  private @Nullable String compilerRelease;
  private @Nullable String compilerSource;

  private PomReader() {
  }

  /**
   * Parses {@code pom.xml} in the given directory.
   *
   * @return the reader, or {@code null} if there is no pom or it could not be parsed
   */
  static @Nullable PomReader read(Path dir, List<String> diagnostics) {
    Path pom = dir.resolve("pom.xml");
    if (!Files.isRegularFile(pom)) {
      return null;
    }
    PomReader reader = new PomReader();
    try (InputStream in = Files.newInputStream(pom)) {
      Document document = secureBuilder().parse(in);
      Element project = document.getDocumentElement();
      reader.readProperties(project);
      reader.readModules(project);
      reader.readCompilerPlugin(project);
      return reader;
    } catch (IOException | SAXException | ParserConfigurationException e) {
      diagnostics.add("could not parse " + pom + " (" + e.getMessage()
          + "); falling back to layout-based discovery for this module");
      return null;
    }
  }

  private static DocumentBuilder secureBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory.newDocumentBuilder();
  }

  private void readProperties(Element project) {
    Element propertiesElement = firstChild(project, "properties");
    if (propertiesElement == null) {
      return;
    }
    NodeList children = propertiesElement.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node instanceof Element element) {
        properties.put(element.getTagName(), element.getTextContent().strip());
      }
    }
  }

  private void readModules(Element project) {
    Element modulesElement = firstChild(project, "modules");
    if (modulesElement == null) {
      return;
    }
    for (Element module : childrenNamed(modulesElement, "module")) {
      String text = module.getTextContent().strip();
      if (!text.isEmpty()) {
        modules.add(text);
      }
    }
  }

  private void readCompilerPlugin(Element project) {
    for (Element build : childrenNamed(project, "build")) {
      for (Element plugins : childrenNamed(build, "plugins")) {
        for (Element plugin : childrenNamed(plugins, "plugin")) {
          Element artifactId = firstChild(plugin, "artifactId");
          if (artifactId == null
              || !"maven-compiler-plugin".equals(artifactId.getTextContent().strip())) {
            continue;
          }
          Element configuration = firstChild(plugin, "configuration");
          if (configuration == null) {
            continue;
          }
          compilerRelease = textOf(firstChild(configuration, "release"));
          compilerSource = textOf(firstChild(configuration, "source"));
        }
      }
    }
  }

  /** The declared child module directory names, in declaration order. */
  List<String> modules() {
    return List.copyOf(modules);
  }

  /**
   * The Java language level this pom declares, or {@code null} if it declares none.
   *
   * <p>Checked in the order Maven itself resolves them, and property placeholders like
   * {@code ${java.version}} are followed one hop into {@code <properties>}.
   */
  @Nullable String release() {
    return firstDeclared(
        resolve(compilerRelease),
        resolve(properties.get("maven.compiler.release")),
        resolve(compilerSource),
        resolve(properties.get("maven.compiler.source")),
        resolve(properties.get("java.version")));
  }

  /**
   * The first candidate that is neither {@code null} nor blank.
   *
   * <p>Deliberately a varargs array and not {@code List.of(...)}: every candidate here is
   * absent-by-design in most poms, and {@code List.of} throws on a null element (codekoll's own
   * CK-IMMUTABLE-FACTORY-NULL rule). A pom that declares no compiler plugin is the common case,
   * not an error, so it must return {@code null} rather than throw.
   */
  private static @Nullable String firstDeclared(@Nullable String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return null;
  }

  private @Nullable String resolve(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String text = value.strip();
    if (text.startsWith("${") && text.endsWith("}")) {
      return properties.get(text.substring(2, text.length() - 1));
    }
    return text;
  }

  private static @Nullable String textOf(@Nullable Element element) {
    return element == null ? null : element.getTextContent().strip();
  }

  private static @Nullable Element firstChild(Element parent, String name) {
    List<Element> matches = childrenNamed(parent, name);
    return matches.isEmpty() ? null : matches.get(0);
  }

  private static List<Element> childrenNamed(Element parent, String name) {
    List<Element> result = new ArrayList<>();
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element element && name.equals(element.getTagName())) {
        result.add(element);
      }
    }
    return result;
  }
}
