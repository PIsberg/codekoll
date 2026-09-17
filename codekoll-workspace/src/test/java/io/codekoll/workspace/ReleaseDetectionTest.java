package io.codekoll.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Language-level detection per unit (CLI-SPEC §3.4), including the clamping and the
 * version-file fallbacks.
 *
 * <p>Guessing the level wrong changes which rules can fire, so every fallback either produces a
 * value the repository actually declared or says out loud that it was assumed.
 */
class ReleaseDetectionTest {

  @TempDir
  Path repo;

  private void write(String relativePath, String content) throws IOException {
    Path file = repo.resolve(relativePath);
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  private void source() throws IOException {
    write("src/main/java/Widget.java", "public class Widget { }\n");
  }

  private Workspace discover() {
    return new WorkspaceDiscovery(WorkspaceOptions.defaults()).discover(List.of(repo));
  }

  private SourceUnit root(Workspace workspace) {
    return workspace.units().stream()
        .filter(u -> ".".equals(u.name()))
        .findFirst()
        .orElseThrow();
  }

  private static String diagnostics(Workspace workspace) {
    return String.join("\n", workspace.diagnostics());
  }

  private static int currentFeature() {
    return Runtime.version().feature();
  }

  // ------------------------------------------------------------- pom formats

  @Test
  void legacyOneDotEightSourceBecomesRelease8() throws IOException {
    write("pom.xml", """
        <project><properties>
          <maven.compiler.source>1.8</maven.compiler.source>
        </properties></project>
        """);
    source();

    assertEquals(8, root(discover()).release());
  }

  @Test
  void aPatchVersionIsTruncatedToItsFeatureNumber() throws IOException {
    write("pom.xml", """
        <project><properties>
          <maven.compiler.release>17.0.2</maven.compiler.release>
        </properties></project>
        """);
    source();

    assertEquals(17, root(discover()).release());
  }

  @Test
  void aNonNumericLevelIsTreatedAsUndeclared() throws IOException {
    write("pom.xml", """
        <project><properties>
          <maven.compiler.release>whatever</maven.compiler.release>
        </properties></project>
        """);
    source();

    Workspace workspace = discover();

    assertFalse(root(workspace).releaseDetected());
    assertTrue(diagnostics(workspace).contains("no language level declared"));
  }

  // ---------------------------------------------------------------- clamping

  @Test
  void aLevelBelowWhatJavacAcceptsIsRaisedAndReported() throws IOException {
    write("pom.xml", """
        <project><properties>
          <maven.compiler.source>1.5</maven.compiler.source>
        </properties></project>
        """);
    source();

    Workspace workspace = discover();

    assertEquals(8, root(workspace).release(), "clamped to javac's minimum");
    assertTrue(diagnostics(workspace).contains("below the minimum"),
        "clamping changes which rules fire, so it must be visible: " + diagnostics(workspace));
  }

  @Test
  void aLevelNewerThanTheRunningJdkIsLoweredAndReported() throws IOException {
    write("pom.xml", """
        <project><properties>
          <maven.compiler.release>%d</maven.compiler.release>
        </properties></project>
        """.formatted(currentFeature() + 5));
    source();

    Workspace workspace = discover();

    assertEquals(currentFeature(), root(workspace).release());
    assertTrue(diagnostics(workspace).contains("newer than the running JDK"),
        diagnostics(workspace));
  }

  // ----------------------------------------------------------- version files

  @Test
  void javaVersionFileIsHonoured() throws IOException {
    write(".java-version", "17\n");
    source();

    SourceUnit unit = root(discover());

    assertEquals(17, unit.release(),
        ".java-version holds nothing but the version, so there is no 'java' word to look for");
    assertTrue(unit.releaseDetected(), "a declared level is not a guess");
  }

  @Test
  void sdkmanrcIsHonoured() throws IOException {
    write(".sdkmanrc", "java=17.0.2-tem\n");
    source();

    assertEquals(17, root(discover()).release());
  }

  @Test
  void toolVersionsPicksTheJavaLine() throws IOException {
    write(".tool-versions", "nodejs 18.16.0\njava temurin-17.0.2\n");
    source();

    assertEquals(17, root(discover()).release());
  }

  @Test
  void toolVersionsIgnoresOtherToolsEntirely() throws IOException {
    write(".tool-versions", "nodejs 18.16.0\npython 3.11.0\n");
    source();

    Workspace workspace = discover();

    assertFalse(root(workspace).releaseDetected(),
        "another tool's version must never be read as Java's");
    assertTrue(diagnostics(workspace).contains("no language level declared"));
  }

  @Test
  void aBuildFileBeatsAVersionFile() throws IOException {
    write("pom.xml", """
        <project><properties>
          <maven.compiler.release>21</maven.compiler.release>
        </properties></project>
        """);
    write(".java-version", "17\n");
    source();

    assertEquals(21, root(discover()).release(), "the build file is the more specific declaration");
  }

  @Test
  void commentsInAVersionFileAreSkipped() throws IOException {
    write(".tool-versions", "# java 11\njava 17\n");
    source();

    assertEquals(17, root(discover()).release());
  }
}
