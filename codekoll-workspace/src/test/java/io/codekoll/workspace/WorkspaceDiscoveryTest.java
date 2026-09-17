package io.codekoll.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Discovery against fixture repositories built on disk (CLI-SPEC §3).
 *
 * <p>The fixtures are created per-test rather than committed, because the interesting cases are
 * about directory layout and a layout is what a temp directory can express exactly. Every
 * assertion here is one the Milestone 11 exit criterion names: the right units, the right
 * language level, repo-relative paths, and a diagnostic whenever discovery had to guess.
 */
class WorkspaceDiscoveryTest {

  @TempDir
  Path repo;

  private static final String POM_RELEASE_21 = """
      <project>
        <groupId>t</groupId><artifactId>single</artifactId><version>1</version>
        <properties><maven.compiler.release>21</maven.compiler.release></properties>
      </project>
      """;

  // ---------------------------------------------------------------- fixtures

  private void write(String relativePath, String content) throws IOException {
    Path file = repo.resolve(relativePath);
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  private void javaFile(String relativePath, String className) throws IOException {
    write(relativePath, "public class " + className + " { }\n");
  }

  /** Anchors repo-root detection at the fixture so no ancestor of the temp dir can win. */
  private void gitDir() throws IOException {
    Files.createDirectories(repo.resolve(".git"));
  }

  private Workspace discover() {
    return discover(new Opts());
  }

  private Workspace discover(Opts opts) {
    return new WorkspaceDiscovery(opts.build()).discover(List.of(repo));
  }

  private SourceUnit unit(Workspace workspace, String name) {
    return workspace.units().stream()
        .filter(u -> name.equals(u.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no unit '" + name + "' in " + names(workspace)));
  }

  private static List<String> names(Workspace workspace) {
    return workspace.units().stream().map(SourceUnit::name).toList();
  }

  private static List<String> fileNames(SourceUnit unit) {
    return unit.files().stream()
        .map(p -> String.valueOf(p.getFileName()))
        .sorted()
        .toList();
  }

  private static String diagnostics(Workspace workspace) {
    return String.join("\n", workspace.diagnostics());
  }

  /** Minimal builder so each test states only the option it cares about. */
  private static final class Opts {
    private Path repoRoot;
    private List<String> includes = List.of();
    private List<String> excludes = List.of();
    private boolean includeTests = true;
    private boolean useGitignore = true;
    private int releaseOverride;
    private long maxFileBytes = WorkspaceOptions.DEFAULT_MAX_FILE_BYTES;

    Opts repoRoot(Path value) {
      this.repoRoot = value;
      return this;
    }

    Opts includes(String... globs) {
      this.includes = List.of(globs);
      return this;
    }

    Opts excludes(String... globs) {
      this.excludes = List.of(globs);
      return this;
    }

    Opts includeTests(boolean value) {
      this.includeTests = value;
      return this;
    }

    Opts useGitignore(boolean value) {
      this.useGitignore = value;
      return this;
    }

    Opts release(int value) {
      this.releaseOverride = value;
      return this;
    }

    Opts maxFileBytes(long value) {
      this.maxFileBytes = value;
      return this;
    }

    WorkspaceOptions build() {
      return new WorkspaceOptions(repoRoot, includes, excludes, includeTests, useGitignore,
          releaseOverride, ResolveMode.DISCOVER, "", maxFileBytes);
    }
  }

  // ------------------------------------------------------------ maven layout

  @Test
  void mavenSingleModuleFindsMainAndTestSources() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/com/acme/Widget.java", "Widget");
    javaFile("src/test/java/com/acme/WidgetTest.java", "WidgetTest");

    Workspace workspace = discover();

    assertEquals(List.of("."), names(workspace));
    SourceUnit root = unit(workspace, ".");
    assertEquals(BuildSystem.MAVEN, root.buildSystem());
    assertEquals(21, root.release());
    assertTrue(root.releaseDetected(), "release came from the pom, not a guess");
    assertEquals(List.of("Widget.java", "WidgetTest.java"), fileNames(root));
    assertEquals(2, root.sourceRoots().size());
    assertTrue(root.sourceRoots().get(0).toString().replace('\\', '/').endsWith("src/main/java"),
        "main sources must come first so javac resolves test references against them");
  }

  @Test
  void noTestsOptionDropsTestSourceRoots() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");
    javaFile("src/test/java/WidgetTest.java", "WidgetTest");

    SourceUnit root = unit(discover(new Opts().includeTests(false)), ".");

    assertEquals(List.of("Widget.java"), fileNames(root));
    assertEquals(1, root.sourceRoots().size());
  }

  @Test
  void multiModuleYieldsOneUnitPerModuleSortedByName() throws IOException {
    write("pom.xml", """
        <project>
          <artifactId>parent</artifactId>
          <properties><maven.compiler.release>17</maven.compiler.release></properties>
          <modules><module>core</module><module>app</module></modules>
        </project>
        """);
    write("core/pom.xml", "<project><artifactId>core</artifactId></project>\n");
    write("app/pom.xml", "<project><artifactId>app</artifactId></project>\n");
    javaFile("core/src/main/java/Core.java", "Core");
    javaFile("app/src/main/java/App.java", "App");

    Workspace workspace = discover();

    assertEquals(List.of("app", "core"), names(workspace));
    assertEquals(2, workspace.fileCount());
  }

  @Test
  void childModuleInheritsTheParentLanguageLevel() throws IOException {
    write("pom.xml", """
        <project>
          <artifactId>parent</artifactId>
          <properties><maven.compiler.release>17</maven.compiler.release></properties>
          <modules><module>core</module></modules>
        </project>
        """);
    write("core/pom.xml", "<project><artifactId>core</artifactId></project>\n");
    javaFile("core/src/main/java/Core.java", "Core");

    SourceUnit core = unit(discover(), "core");

    assertEquals(17, core.release());
    assertTrue(core.releaseDetected(), "inherited from the parent pom, so not a guess");
  }

  @Test
  void modulesKeepTheirOwnDifferingLanguageLevels() throws IOException {
    write("pom.xml", """
        <project>
          <artifactId>parent</artifactId>
          <modules><module>legacy</module><module>modern</module></modules>
        </project>
        """);
    write("legacy/pom.xml", """
        <project><artifactId>legacy</artifactId>
          <properties><maven.compiler.release>17</maven.compiler.release></properties>
        </project>
        """);
    write("modern/pom.xml", """
        <project><artifactId>modern</artifactId>
          <properties><maven.compiler.release>21</maven.compiler.release></properties>
        </project>
        """);
    javaFile("legacy/src/main/java/Legacy.java", "Legacy");
    javaFile("modern/src/main/java/Modern.java", "Modern");

    Workspace workspace = discover();

    assertEquals(17, unit(workspace, "legacy").release());
    assertEquals(21, unit(workspace, "modern").release());
  }

  @Test
  void releaseOverrideBeatsEveryDeclaredLevel() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");

    assertEquals(17, unit(discover(new Opts().release(17)), ".").release());
  }

  // ----------------------------------------------------------- gradle layout

  @Test
  void gradleProjectIsDetectedAndItsLanguageLevelRead() throws IOException {
    write("settings.gradle", "rootProject.name = 'demo'\ninclude 'core'\n");
    write("build.gradle", "sourceCompatibility = 17\n");
    javaFile("src/main/java/Demo.java", "Demo");

    Workspace workspace = discover();

    assertEquals(BuildSystem.GRADLE, workspace.buildSystem());
    SourceUnit root = unit(workspace, ".");
    assertEquals(17, root.release());
    assertTrue(root.releaseDetected());
  }

  @Test
  void gradleKotlinDslToolchainIsRead() throws IOException {
    write("settings.gradle.kts", "rootProject.name = \"demo\"\n");
    write("build.gradle.kts", """
        java {
          toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
        }
        """);
    javaFile("src/main/java/Demo.java", "Demo");

    assertEquals(21, unit(discover(), ".").release());
  }

  @Test
  void commentedOutLanguageLevelIsNotRead() throws IOException {
    write("build.gradle", "// sourceCompatibility = 11\nsourceCompatibility = 17\n");
    javaFile("src/main/java/Demo.java", "Demo");

    assertEquals(17, unit(discover(), ".").release());
  }

  // ------------------------------------------------- layouts with no build file

  @Test
  void conventionalLayoutWithoutABuildFileStillWorks() throws IOException {
    gitDir();
    javaFile("src/main/java/Widget.java", "Widget");

    Workspace workspace = discover();

    assertEquals(BuildSystem.CONVENTIONAL, workspace.buildSystem());
    assertEquals(1, workspace.fileCount());
  }

  @Test
  void plainSourceTreeIsAnalyzedAsOneUnit() throws IOException {
    gitDir();
    javaFile("Widget.java", "Widget");
    javaFile("helpers/Helper.java", "Helper");

    Workspace workspace = discover();

    assertEquals(BuildSystem.PLAIN, workspace.buildSystem());
    assertEquals(2, workspace.fileCount());
  }

  @Test
  void undeclaredLanguageLevelIsGuessedAndSaidOutLoud() throws IOException {
    gitDir();
    javaFile("src/main/java/Widget.java", "Widget");

    Workspace workspace = discover();
    SourceUnit root = unit(workspace, ".");

    assertEquals(Runtime.version().feature(), root.release());
    assertFalse(root.releaseDetected(), "a guessed level must not claim to be detected");
    assertTrue(diagnostics(workspace).contains("no language level declared"),
        "guessing silently is the failure this module exists to prevent: " + diagnostics(workspace));
  }

  @Test
  void unparseablePomIsReportedAndDiscoveryContinues() throws IOException {
    write("pom.xml", "<project><artifactId>broken</artifactId>\n");
    javaFile("src/main/java/Widget.java", "Widget");

    Workspace workspace = discover();

    assertEquals(1, workspace.fileCount(), "a broken pom must not cost us the source files");
    assertTrue(diagnostics(workspace).contains("could not parse"),
        "the parse failure must be visible: " + diagnostics(workspace));
  }

  // ---------------------------------------------------------- file selection

  @Test
  void buildOutputDirectoriesAreNeverAnalyzed() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");
    javaFile("target/classes/Stale.java", "Stale");
    javaFile("target/generated-sources/Gen.java", "Gen");
    javaFile("build/Old.java", "Old");
    javaFile("out/Out.java", "Out");

    assertEquals(List.of("Widget.java"), fileNames(unit(discover(), ".")));
  }

  @Test
  void moduleInfoIsSkipped() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");
    write("src/main/java/module-info.java", "module demo { }\n");

    assertEquals(List.of("Widget.java"), fileNames(unit(discover(), ".")));
  }

  @Test
  void generatedFilesAreSkippedByTheirBanner() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");
    write("src/main/java/Parser.java", """
        // Generated by javacc. DO NOT EDIT.
        public class Parser { }
        """);

    assertEquals(List.of("Widget.java"), fileNames(unit(discover(), ".")));
  }

  /**
   * Sample projects stored as test data are not modules of the project storing them — codekoll's
   * own fixture repositories are exactly this shape, and without the rule they turn into sixteen
   * units and their deliberate sample code into findings.
   */
  @Test
  void sourceLayoutsInsideTestResourcesAreNotModules() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");
    write("src/test/resources/repos/sample/pom.xml", POM_RELEASE_21);
    javaFile("src/test/resources/repos/sample/src/main/java/Sample.java", "Sample");

    Workspace workspace = discover();

    assertEquals(List.of("."), names(workspace));
    assertEquals(List.of("Widget.java"), fileNames(unit(workspace, ".")));
  }

  @Test
  void oversizedFilesAreSkippedWithADiagnostic() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");
    write("src/main/java/Huge.java", "public class Huge { }\n" + " ".repeat(400));

    Workspace workspace = discover(new Opts().maxFileBytes(64));

    assertEquals(List.of("Widget.java"), fileNames(unit(workspace, ".")));
    assertTrue(diagnostics(workspace).contains("exceeds the"),
        "skipping for size must be reported: " + diagnostics(workspace));
  }

  @Test
  void gitignoredSourcesAreExcludedUnlessTurnedOff() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    write(".gitignore", "src/main/java/vendor/\n");
    javaFile("src/main/java/Widget.java", "Widget");
    javaFile("src/main/java/vendor/Vendored.java", "Vendored");

    assertEquals(List.of("Widget.java"), fileNames(unit(discover(), ".")));
    assertEquals(List.of("Vendored.java", "Widget.java"),
        fileNames(unit(discover(new Opts().useGitignore(false)), ".")));
  }

  @Test
  void excludeGlobDropsMatchingSources() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");
    javaFile("src/main/java/legacy/Old.java", "Old");

    SourceUnit root = unit(discover(new Opts().excludes("**/legacy/**")), ".");

    assertEquals(List.of("Widget.java"), fileNames(root));
  }

  @Test
  void includeGlobNarrowsTheDiscoveredSet() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");
    javaFile("src/main/java/legacy/Old.java", "Old");

    SourceUnit root = unit(discover(new Opts().includes("**/legacy/**")), ".");

    assertEquals(List.of("Old.java"), fileNames(root));
  }

  @Test
  void excludeBeatsIncludeWhenBothMatch() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/legacy/Old.java", "Old");
    javaFile("src/main/java/legacy/Keep.java", "Keep");

    SourceUnit root = unit(
        discover(new Opts().includes("**/legacy/**").excludes("**/Old.java")), ".");

    assertEquals(List.of("Keep.java"), fileNames(root));
  }

  @Test
  void unitsWithNoFilesAreDropped() throws IOException {
    write("pom.xml", """
        <project><artifactId>parent</artifactId>
          <modules><module>core</module><module>empty</module></modules>
        </project>
        """);
    write("core/pom.xml", "<project><artifactId>core</artifactId></project>\n");
    write("empty/pom.xml", "<project><artifactId>empty</artifactId></project>\n");
    javaFile("core/src/main/java/Core.java", "Core");
    Files.createDirectories(repo.resolve("empty/src/main/java"));

    assertEquals(List.of("core"), names(discover()));
  }

  // ---------------------------------------------------------- root and paths

  @Test
  void repoRootIsDetectedFromTheGitDirectory() throws IOException {
    gitDir();
    write("nested/module/pom.xml", POM_RELEASE_21);
    javaFile("nested/module/src/main/java/Widget.java", "Widget");

    Workspace workspace = discover();

    assertEquals(repo.toRealPath(), workspace.repoRoot().toRealPath());
    assertEquals(List.of("nested/module"), names(workspace),
        "unit names are repo-relative with forward slashes");
  }

  @Test
  void explicitRepoRootOptionWins() throws IOException {
    write("sub/pom.xml", POM_RELEASE_21);
    javaFile("sub/src/main/java/Widget.java", "Widget");

    Path sub = repo.resolve("sub");
    Workspace workspace =
        new WorkspaceDiscovery(new Opts().repoRoot(sub).build()).discover(List.of(repo));

    assertEquals(sub.toRealPath(), workspace.repoRoot().toRealPath());
  }

  @Test
  void reportedPathsAreRepoRelativeWithForwardSlashes() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/com/acme/Widget.java", "Widget");

    Workspace workspace = discover();
    String relative = workspace.relativize(unit(workspace, ".").files().get(0));

    assertEquals("src/main/java/com/acme/Widget.java", relative);
    assertFalse(relative.contains("\\"), "backslashes must never reach a report");
  }

  @Test
  void sourcePathCoversEveryDiscoveredRoot() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");
    javaFile("src/test/java/WidgetTest.java", "WidgetTest");

    assertEquals(2, discover().sourcePath().size(),
        "both roots go on -sourcepath so cross-root references attribute");
  }

  @Test
  void aMissingPathIsReportedRatherThanIgnored() {
    Path missing = repo.resolve("does-not-exist");

    Workspace workspace =
        new WorkspaceDiscovery(new Opts().build()).discover(List.of(missing));

    assertTrue(String.join("\n", workspace.diagnostics()).contains("does not exist"),
        "a path the user named but that is absent must be reported");
  }

  /**
   * Found by running codekoll on codekoll through discovery: a shell that expands a glob into the
   * positional list hands over {@code pom.xml}, javac rejects it with
   * {@code IllegalArgumentException: Compilation unit is not of SOURCE kind}, and the run dies
   * with a stack trace instead of a message.
   */
  @Test
  void aNamedFileThatIsNotJavaSourceIsReportedNotCompiled() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");

    Workspace workspace = new WorkspaceDiscovery(new Opts().repoRoot(repo).build())
        .discover(List.of(repo.resolve("pom.xml")));

    assertTrue(workspace.units().stream().flatMap(u -> u.files().stream()).findAny().isEmpty(),
        "a pom is not a compilation unit: " + workspace.units());
    assertTrue(diagnostics(workspace).contains("not Java source, ignored"),
        diagnostics(workspace));
  }

  /** The same expansion also names a directory twice; analyzing it twice doubles every finding. */
  @Test
  void overlappingPathsAnalyzeEachFileOnce() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");

    Workspace workspace = new WorkspaceDiscovery(new Opts().repoRoot(repo).build())
        .discover(List.of(repo, repo.resolve("src"), repo.resolve("src/main/java")));

    assertEquals(1, workspace.fileCount(), "one file, named through three overlapping paths");
    assertEquals(1, unit(workspace, ".").sourceRoots().size());
  }

  @Test
  void anExplicitlyNamedFileBecomesItsOwnUnit() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");
    Path file = repo.resolve("src/main/java/Widget.java");

    Workspace workspace = new WorkspaceDiscovery(new Opts().build()).discover(List.of(file));

    assertEquals(List.of("<files>"), names(workspace));
    assertEquals(1, workspace.fileCount());
  }

  @Test
  void discoveryIsDeterministic() throws IOException {
    write("pom.xml", """
        <project><artifactId>parent</artifactId>
          <modules><module>b</module><module>a</module></modules>
        </project>
        """);
    write("a/pom.xml", "<project><artifactId>a</artifactId></project>\n");
    write("b/pom.xml", "<project><artifactId>b</artifactId></project>\n");
    javaFile("a/src/main/java/A.java", "A");
    javaFile("b/src/main/java/B.java", "B");

    Workspace first = discover();
    Workspace second = discover();

    assertEquals(names(first), names(second));
    assertEquals(List.of("a", "b"), names(first));
    assertEquals(unit(first, "a").files(), unit(second, "a").files());
  }

  @Test
  void discoveredClasspathPicksUpExistingBuildOutput() throws IOException {
    write("pom.xml", POM_RELEASE_21);
    javaFile("src/main/java/Widget.java", "Widget");
    Files.createDirectories(repo.resolve("target/classes"));

    SourceUnit root = unit(discover(), ".");

    assertFalse(root.classpath().isEmpty(),
        "an already-built target/classes is exactly what discover mode is for");
    assertNotEquals("", root.classpathString());
  }
}
