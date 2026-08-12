package io.codekoll.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * The CLI half of Milestone 11: what {@code --print-workspace} says about the committed fixture
 * repositories, and that findings come back repo-relative.
 *
 * <p>The fixtures under {@code src/test/resources/repos} are real directory layouts rather than
 * temp directories built in code, because this is the assertion about what a *committed*
 * repository looks like — the same thing a user points codekoll at. Two values in the output are
 * machine-dependent and normalized before comparison: the absolute repo root, and the running
 * JDK's feature version, which is what discovery falls back to when a repo declares no language
 * level.
 */
class WorkspaceCliTest {

  @TempDir
  Path work;

  private static final Path REPOS = Path.of("src", "test", "resources", "repos");

  /**
   * Codekoll writes its report to its own stdout writer rather than picocli's, so every output
   * assertion here goes through {@code --output} — which is also the path CI uses.
   */
  private String outputOf(String... args) throws IOException {
    Path file = work.resolve("out-" + args.length + "-" + System.nanoTime() + ".txt");
    String[] all = new String[args.length + 2];
    System.arraycopy(args, 0, all, 0, args.length);
    all[args.length] = "--output";
    all[args.length + 1] = file.toString();
    int code = new CommandLine(new Main()).execute(all);
    assertTrue(code == 0 || code == 1, "unexpected exit code " + code);
    return Files.readString(file);
  }

  private String workspaceJson(String fixture) throws IOException {
    Path repo = REPOS.resolve(fixture).toAbsolutePath().normalize();
    String actual = outputOf("--print-workspace", "--format", "json",
        "--repo", repo.toString(), repo.toString());
    return normalize(actual, repo);
  }

  /**
   * The snapshot is a checked-in text file, so git hands it back with CRLF on Windows and LF on
   * the CI runner. Both sides are folded to {@code \n} before comparison; without that the tests
   * pass on whichever platform generated them and fail on the other.
   */
  private static String expected(String fixture) throws IOException {
    return unifyLineEndings(
        Files.readString(REPOS.resolve(fixture).resolve("expected-workspace.json")));
  }

  private static String unifyLineEndings(String text) {
    return text.lines().collect(java.util.stream.Collectors.joining("\n")).strip();
  }

  /**
   * Removes the two values that legitimately differ per machine: the absolute repo root, and the
   * running JDK's feature version, which is what discovery falls back to when a repository
   * declares no language level. Separators are folded to {@code /} so one snapshot serves both
   * the Windows development machine and the Linux CI runner.
   */
  private static String normalize(String json, Path repo) {
    String text = json.lines()
        .filter(line -> !line.contains("\"repoRoot\""))
        .collect(java.util.stream.Collectors.joining("\n"));
    return text.replace(repo.toString().replace("\\", "\\\\"), "<repo>")
        .replace("\\\\", "/")
        .replace("\"release\": " + Runtime.version().feature() + ",", "\"release\": <jdk>,")
        .replace("assuming --release " + Runtime.version().feature(), "assuming --release <jdk>")
        .strip();
  }

  @Test
  void mavenSingleModuleReportsOneUnitWithBothSourceSets() throws IOException {
    assertEquals(expected("maven-single"), workspaceJson("maven-single"));
  }

  @Test
  void mavenMultiModuleKeepsPerModuleLanguageLevels() throws IOException {
    assertEquals(expected("maven-multimodule"), workspaceJson("maven-multimodule"));
  }

  @Test
  void gradleKotlinDslMultiProjectReadsTheRootToolchain() throws IOException {
    assertEquals(expected("gradle-kts-multiproject"), workspaceJson("gradle-kts-multiproject"));
  }

  @Test
  void gradleGroovyProjectReadsSourceCompatibility() throws IOException {
    assertEquals(expected("gradle-groovy"), workspaceJson("gradle-groovy"));
  }

  @Test
  void plainSourceTreeIsOneUnitRootedAtTheRepo() throws IOException {
    assertEquals(expected("plain-sources"), workspaceJson("plain-sources"));
  }

  @Test
  void conventionalLayoutWithoutABuildFileIsRecognized() throws IOException {
    assertEquals(expected("no-build-file"), workspaceJson("no-build-file"));
  }

  /** An unreadable build file must degrade to the layout and say so, never abort the run. */
  @Test
  void brokenPomDegradesToTheLayoutAndSaysSo() throws IOException {
    String actual = workspaceJson("broken-pom");

    assertEquals(expected("broken-pom"), actual);
    assertTrue(actual.contains("falling back to layout-based discovery"), actual);
  }

  // -------------------------------------------------------------- reported paths

  @Test
  void findingsAreReportedRepoRelative() throws IOException {
    Path repo = fixtureWithABug();

    String out = outputOf("--fail-on", "never", "--repo", repo.toString(), repo.toString());

    assertTrue(out.contains("src/main/java/app/Weak.java"), out);
    assertFalse(out.contains(repo.toString()), "no absolute prefix: " + out);
  }

  @Test
  void absolutePathsOptionRestoresTheOldBehaviour() throws IOException {
    Path repo = fixtureWithABug();

    String out = outputOf("--fail-on", "never", "--absolute-paths",
        "--repo", repo.toString(), repo.toString());

    assertTrue(out.contains(repo.resolve("src").toString()), out);
  }

  @Test
  void sarifCarriesRepoRelativeUris() throws IOException {
    Path repo = fixtureWithABug();

    String out = outputOf("--format", "sarif", "--fail-on", "never",
        "--repo", repo.toString(), repo.toString());

    assertTrue(out.contains("\"uri\": \"src/main/java/app/Weak.java\""), out);
  }

  @Test
  void noTestsSkipsTestSourceRoots() throws IOException {
    Path repo = REPOS.resolve("maven-single").toAbsolutePath().normalize();

    String out = outputOf("--print-workspace", "--no-tests",
        "--repo", repo.toString(), repo.toString());

    assertTrue(out.contains("src/main/java"), out);
    assertFalse(out.contains("src/test/java"), out);
  }

  @Test
  void includeGlobNarrowsWhatIsAnalyzed() throws IOException {
    Path repo = REPOS.resolve("maven-multimodule").toAbsolutePath().normalize();

    String out = outputOf("--print-workspace", "--include", "web/**",
        "--repo", repo.toString(), repo.toString());

    assertTrue(out.contains("web/src/main/java"), out);
    assertFalse(out.contains("core/src/main/java"), out);
  }

  @Test
  void aPositionalPathIsOptional() throws IOException {
    // Milestone 11 makes `codekoll` in a repo root the everyday invocation; arity 0..* is what
    // lets it parse at all, and a usage error would exit 2 before discovery ever ran.
    String out = outputOf("--print-workspace", "--repo", work.toString());

    assertTrue(out.contains("repo root:"), out);
  }

  /**
   * {@code build} mode runs the target repository's build tool. Until the CLI-SPEC §4.3 trust
   * gate exists, accepting the flag and quietly doing something else would be the exact failure
   * that gate is for, so it is refused with a usage error.
   */
  @Test
  void resolveBuildIsRefusedRatherThanSilentlyDowngraded() throws IOException {
    Path file = work.resolve("refused.txt");

    int code = new CommandLine(new Main()).execute("--resolve", "build", "--print-workspace",
        "--output", file.toString(), work.toString());

    assertEquals(2, code);
    assertTrue(Files.readString(file).contains("not implemented yet"), Files.readString(file));
  }

  @Test
  void resolveNoneLeavesTheClasspathToTheCaller() throws IOException {
    Path repo = REPOS.resolve("maven-single").toAbsolutePath().normalize();

    String out = outputOf("--print-workspace", "--format", "json", "--resolve", "none",
        "--repo", repo.toString(), repo.toString());

    assertTrue(out.contains("\"units\""), out);
  }

  @Test
  void helpDocumentsTheWorkspaceOptions() {
    StringWriter sw = new StringWriter();
    CommandLine cmd = new CommandLine(new Main());
    cmd.setOut(new PrintWriter(sw, true));

    assertEquals(0, cmd.execute("--help"));
    for (String option : new String[] {"--repo", "--include", "--exclude", "--no-tests",
        "--no-gitignore", "--absolute-paths", "--print-workspace", "--verbose"}) {
      assertTrue(sw.toString().contains(option), "help must document " + option);
    }
  }

  /** A one-file repository whose single class carries a finding every rule set reports. */
  private Path fixtureWithABug() throws IOException {
    Path source = work.resolve("src/main/java/app/Weak.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, """
        package app;

        import java.security.MessageDigest;

        public class Weak {
          public void hash() throws Exception {
            MessageDigest.getInstance("MD5");
          }
        }
        """);
    Files.writeString(work.resolve("pom.xml"), """
        <project>
          <groupId>t</groupId><artifactId>bug</artifactId><version>1</version>
          <properties><maven.compiler.release>21</maven.compiler.release></properties>
        </project>
        """);
    return work.toAbsolutePath().normalize();
  }
}
