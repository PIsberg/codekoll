package io.codekoll.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reading language level and modules out of a {@code pom.xml}, safely (CLI-SPEC §3.2, §14). */
class PomReaderTest {

  @TempDir
  Path dir;

  private final List<String> diagnostics = new ArrayList<>();

  private PomReader read(String pom) throws IOException {
    Files.writeString(dir.resolve("pom.xml"), pom, StandardCharsets.UTF_8);
    return PomReader.read(dir, diagnostics);
  }

  @Test
  void absentPomReadsAsAbsentWithoutComplaint() {
    assertNull(PomReader.read(dir, diagnostics));
    assertTrue(diagnostics.isEmpty(), "no pom is normal, not a problem to report");
  }

  @Test
  void releaseComesFromTheCompilerReleaseProperty() throws IOException {
    PomReader pom = read("""
        <project>
          <properties><maven.compiler.release>21</maven.compiler.release></properties>
        </project>
        """);
    assertNotNull(pom);
    assertEquals("21", pom.release());
  }

  @Test
  void aPomDeclaringNoLanguageLevelReturnsNullRatherThanThrowing() throws IOException {
    PomReader pom = read("<project><artifactId>plain</artifactId></project>\n");

    assertNotNull(pom);
    assertNull(pom.release(), "the common case must not be an exception");
  }

  @Test
  void compilerPluginConfigurationWinsOverProperties() throws IOException {
    PomReader pom = read("""
        <project>
          <properties><maven.compiler.release>17</maven.compiler.release></properties>
          <build><plugins><plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration><release>21</release></configuration>
          </plugin></plugins></build>
        </project>
        """);
    assertNotNull(pom);
    assertEquals("21", pom.release(), "an explicit plugin release is the most specific declaration");
  }

  @Test
  void sourceIsUsedWhenNoReleaseIsDeclared() throws IOException {
    PomReader pom = read("""
        <project>
          <properties><maven.compiler.source>17</maven.compiler.source></properties>
        </project>
        """);
    assertNotNull(pom);
    assertEquals("17", pom.release());
  }

  @Test
  void javaVersionPropertyIsTheLastResort() throws IOException {
    PomReader pom = read("""
        <project><properties><java.version>17</java.version></properties></project>
        """);
    assertNotNull(pom);
    assertEquals("17", pom.release());
  }

  @Test
  void propertyPlaceholderIsFollowedOneHop() throws IOException {
    PomReader pom = read("""
        <project>
          <properties>
            <jdk.level>21</jdk.level>
            <maven.compiler.release>${jdk.level}</maven.compiler.release>
          </properties>
        </project>
        """);
    assertNotNull(pom);
    assertEquals("21", pom.release());
  }

  @Test
  void modulesAreReadInDeclarationOrder() throws IOException {
    PomReader pom = read("""
        <project>
          <modules>
            <module>core</module>
            <module>app</module>
          </modules>
        </project>
        """);
    assertNotNull(pom);
    assertEquals(List.of("core", "app"), pom.modules());
  }

  @Test
  void malformedXmlIsReportedAndReadsAsAbsent() throws IOException {
    assertNull(read("<project><artifactId>truncated</artifactId>\n"));
    assertFalse(diagnostics.isEmpty(), "an unparseable pom must be reported, never swallowed");
    assertTrue(String.join("\n", diagnostics).contains("could not parse"));
  }

  /**
   * The hardening asserted here is what {@code config/spotbugs-exclude.xml} cites when it excludes
   * XXE_DOCUMENT for {@code PomReader.read}: find-sec-bugs cannot see across the helper method, so
   * this test is the actual guarantee.
   */
  @Test
  void rejectsExternalEntities() throws IOException {
    Path secret = dir.resolve("secret.txt");
    Files.writeString(secret, "TOP-SECRET-VALUE", StandardCharsets.UTF_8);

    PomReader pom = read("""
        <?xml version="1.0"?>
        <!DOCTYPE project [ <!ENTITY xxe SYSTEM "%s"> ]>
        <project><artifactId>&xxe;</artifactId></project>
        """.formatted(secret.toUri()));

    assertNull(pom, "a pom declaring a DOCTYPE must be refused, not parsed");
    assertFalse(diagnostics.isEmpty(), "the refusal must be visible in the run output");
    assertFalse(String.join("\n", diagnostics).contains("TOP-SECRET-VALUE"),
        "the external entity must never have been expanded");
  }

  @Test
  void doesNotFetchExternalDoctypes() throws IOException {
    PomReader pom = read("""
        <?xml version="1.0"?>
        <!DOCTYPE project SYSTEM "http://127.0.0.1:1/evil.dtd">
        <project><artifactId>x</artifactId></project>
        """);

    assertNull(pom, "an external DTD reference must be refused before any network access");
    assertFalse(diagnostics.isEmpty());
  }
}
