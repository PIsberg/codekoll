package io.codekoll.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Textual reading of Gradle scripts (CLI-SPEC §3.2). Build scripts are programs; everything here
 * is best-effort by construction and must return {@code null} rather than guess.
 */
class GradleReaderTest {

  @TempDir
  Path dir;

  private void write(String name, String content) throws IOException {
    Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
  }

  @Test
  void noScriptMeansNothingDeclared() {
    assertNull(GradleReader.release(dir));
    assertEquals(List.of(), GradleReader.subprojects(dir));
    assertFalse(GradleReader.isGradleDir(dir));
  }

  @Test
  void sourceCompatibilityIsRead() throws IOException {
    write("build.gradle", "sourceCompatibility = 17\n");
    assertEquals("17", GradleReader.release(dir));
  }

  @Test
  void javaVersionConstantIsRead() throws IOException {
    write("build.gradle", "sourceCompatibility = JavaVersion.VERSION_21\n");
    assertEquals("21", GradleReader.release(dir));
  }

  @Test
  void legacyOneDotEightBecomesEight() throws IOException {
    write("build.gradle", "sourceCompatibility = JavaVersion.VERSION_1_8\n");
    assertEquals("8", GradleReader.release(dir));
  }

  @Test
  void toolchainLanguageVersionIsRead() throws IOException {
    write("build.gradle.kts", """
        java {
          toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
        }
        """);
    assertEquals("21", GradleReader.release(dir));
  }

  @Test
  void explicitReleaseIsRead() throws IOException {
    write("build.gradle.kts", "tasks.withType<JavaCompile> { options.release.set(21) }\n");
    assertEquals("21", GradleReader.release(dir));
  }

  @Test
  void kotlinDslIsPreferredWhenBothScriptsExist() throws IOException {
    write("build.gradle.kts", "sourceCompatibility = 21\n");
    write("build.gradle", "sourceCompatibility = 11\n");
    assertEquals("21", GradleReader.release(dir));
  }

  @Test
  void lineCommentedDeclarationsAreNotRead() throws IOException {
    write("build.gradle", "// sourceCompatibility = 11\n");
    assertNull(GradleReader.release(dir));
  }

  @Test
  void blockCommentedDeclarationsAreNotRead() throws IOException {
    write("build.gradle", "/* sourceCompatibility = 11 */\n");
    assertNull(GradleReader.release(dir));
  }

  @Test
  void subprojectsAreReadFromSettings() throws IOException {
    write("settings.gradle", """
        rootProject.name = 'demo'
        include 'core', 'app'
        """);
    assertEquals(List.of("core", "app"), GradleReader.subprojects(dir));
  }

  @Test
  void colonSeparatedSubprojectsBecomePaths() throws IOException {
    write("settings.gradle.kts", "include(\":services:auth\")\n");
    assertEquals(List.of("services/auth"), GradleReader.subprojects(dir));
  }

  @Test
  void commentedIncludesAreIgnored() throws IOException {
    write("settings.gradle", "// include 'ghost'\ninclude 'real'\n");
    assertEquals(List.of("real"), GradleReader.subprojects(dir));
  }

  @Test
  void aSettingsFileAloneMakesItAGradleDirectory() throws IOException {
    write("settings.gradle", "rootProject.name = 'demo'\n");
    assertTrue(GradleReader.isGradleDir(dir));
  }
}
