package io.codekoll.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The value types: {@link Workspace}, {@link SourceUnit}, {@link BuildSystem}, options. */
class WorkspaceModelTest {

  @TempDir
  Path dir;

  private static SourceUnit unit(String name, Path moduleDir, List<Path> files) {
    return new SourceUnit(name, moduleDir, BuildSystem.MAVEN, List.of(moduleDir), files,
        21, true, List.of());
  }

  private static Workspace workspace(Path root, List<SourceUnit> units) {
    return new Workspace(root, BuildSystem.MAVEN, units, List.of(), List.of());
  }

  // ------------------------------------------------------------- Workspace

  @Test
  void fileCountSumsEveryUnit() {
    Path root = Path.of("/repo");
    Workspace workspace = workspace(root, List.of(
        unit("a", root.resolve("a"), List.of(Path.of("/repo/a/A.java"))),
        unit("b", root.resolve("b"),
            List.of(Path.of("/repo/b/B.java"), Path.of("/repo/b/C.java")))));

    assertEquals(3, workspace.fileCount());
  }

  @Test
  void relativizeUsesForwardSlashesOnEveryPlatform() {
    Path root = dir;
    Workspace workspace = workspace(root, List.of());

    String relative = workspace.relativize(root.resolve("src").resolve("main").resolve("A.java"));

    assertEquals("src/main/A.java", relative);
    assertFalse(relative.contains("\\"));
  }

  @Test
  void relativizeLeavesPathsOutsideTheRootAbsolute() {
    Workspace workspace = workspace(dir.resolve("inner"), List.of());
    Path outside = dir.resolve("elsewhere").resolve("A.java").toAbsolutePath();

    assertTrue(workspace.relativize(outside).endsWith("elsewhere/A.java"));
  }

  @Test
  void relativizeRendersTheRootItselfAsDot() {
    Workspace workspace = workspace(dir, List.of());

    // An empty string here reaches --print-workspace as a source root with no name.
    assertEquals(".", workspace.relativize(dir));
  }

  @Test
  void workspaceCollectionsAreDefensivelyCopied() {
    List<SourceUnit> units = new ArrayList<>();
    List<String> diagnostics = new ArrayList<>();
    Workspace workspace =
        new Workspace(dir, BuildSystem.MAVEN, units, new ArrayList<>(), diagnostics);

    units.add(unit("late", dir, List.of()));
    diagnostics.add("late");

    assertEquals(0, workspace.units().size(), "a record must not alias caller-mutable state");
    assertEquals(0, workspace.diagnostics().size());
    assertThrows(UnsupportedOperationException.class,
        () -> workspace.units().add(unit("x", dir, List.of())));
  }

  // ------------------------------------------------------------ SourceUnit

  @Test
  void classpathStringJoinsWithThePlatformSeparator() {
    SourceUnit withCp = unit("a", dir, List.of())
        .withClasspath(List.of(Path.of("one.jar"), Path.of("two.jar")));

    assertEquals("one.jar" + File.pathSeparator + "two.jar", withCp.classpathString());
  }

  @Test
  void classpathStringIsEmptyWhenThereIsNoClasspath() {
    assertEquals("", unit("a", dir, List.of()).classpathString());
  }

  @Test
  void withClasspathKeepsEverythingElse() {
    SourceUnit original = unit("core", dir, List.of(dir.resolve("A.java")));
    SourceUnit resolved = original.withClasspath(List.of(Path.of("dep.jar")));

    assertEquals(original.name(), resolved.name());
    assertEquals(original.release(), resolved.release());
    assertEquals(original.files(), resolved.files());
    assertEquals(List.of(Path.of("dep.jar")), resolved.classpath());
  }

  @Test
  void sourceUnitCollectionsAreDefensivelyCopied() {
    List<Path> files = new ArrayList<>();
    SourceUnit built = new SourceUnit("a", dir, BuildSystem.MAVEN, List.of(), files,
        21, true, List.of());

    files.add(Path.of("Sneaky.java"));

    assertEquals(0, built.files().size());
  }

  // ----------------------------------------------------------- BuildSystem

  @Test
  void buildSystemDetectsMavenAndGradle() throws Exception {
    assertNull(BuildSystem.detectIn(dir));

    Files.writeString(dir.resolve("build.gradle.kts"), "\n");
    assertEquals(BuildSystem.GRADLE, BuildSystem.detectIn(dir));

    Files.writeString(dir.resolve("pom.xml"), "<project/>\n");
    assertEquals(BuildSystem.MAVEN, BuildSystem.detectIn(dir),
        "a repo carrying both is treated as Maven: the pom is what we can read reliably");
  }

  @Test
  void everyBuildSystemHasAHumanLabel() {
    for (BuildSystem value : BuildSystem.values()) {
      assertFalse(value.label().isBlank(), value + " needs a label for --print-workspace");
    }
  }

  // ------------------------------------------------- ResolveMode and options

  @Test
  void resolveModeParsesCaseInsensitively() {
    assertEquals(ResolveMode.DISCOVER, ResolveMode.parse("discover"));
    assertEquals(ResolveMode.BUILD, ResolveMode.parse("BUILD"));
    assertEquals(ResolveMode.AUTO, ResolveMode.parse("Auto"));
    assertEquals(ResolveMode.NONE, ResolveMode.parse("none"));
  }

  @Test
  void resolveModeRejectsUnknownValuesWithAUsefulMessage() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> ResolveMode.parse("maven"));

    assertTrue(thrown.getMessage().contains("maven"), "the message must quote what was given");
    assertTrue(thrown.getMessage().contains("discover"), "and name the valid alternatives");
  }

  @Test
  void defaultOptionsAreTheHermeticSafeOnes() {
    WorkspaceOptions defaults = WorkspaceOptions.defaults();

    assertNull(defaults.repoRoot());
    assertEquals(ResolveMode.DISCOVER, defaults.resolve(),
        "the default must never be able to execute the target's build");
    assertTrue(defaults.includeTests());
    assertTrue(defaults.useGitignore());
    assertEquals(0, defaults.releaseOverride());
    assertEquals(WorkspaceOptions.DEFAULT_MAX_FILE_BYTES, defaults.maxFileBytes());
  }
}
