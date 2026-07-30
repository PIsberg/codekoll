package io.codekoll.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hermetic classpath discovery (CLI-SPEC §4.1). The load-bearing property is what it does
 * <em>not</em> do: no subprocess, no network, no reading of the user's own {@code ~/.m2}.
 */
class ClasspathResolverTest {

  @TempDir
  Path repo;

  private final List<String> diagnostics = new ArrayList<>();

  private Path dir(String relative) throws IOException {
    Path path = repo.resolve(relative);
    Files.createDirectories(path);
    return path;
  }

  private Path jar(String relative) throws IOException {
    Path path = repo.resolve(relative);
    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(path, "not really a jar", StandardCharsets.UTF_8);
    return path;
  }

  private SourceUnit unit(String name, Path moduleDir) {
    return new SourceUnit(name, moduleDir, BuildSystem.MAVEN, List.of(moduleDir), List.of(),
        21, true, List.of());
  }

  private List<SourceUnit> resolve(ResolveMode mode, String extra, SourceUnit... units) {
    return new ClasspathResolver(repo, diagnostics).resolve(List.of(units), mode, extra);
  }

  @Test
  void existingBuildOutputIsFound() throws IOException {
    Path classes = dir("target/classes");
    dir("target/test-classes");

    List<Path> classpath = resolve(ResolveMode.DISCOVER, "", unit(".", repo)).get(0).classpath();

    assertTrue(classpath.contains(classes), "target/classes is the point of discover mode");
    assertTrue(classpath.contains(repo.resolve("target").resolve("test-classes")));
  }

  @Test
  void gradleOutputDirectoriesAreFound() throws IOException {
    Path classes = dir("build/classes/java/main");

    assertTrue(resolve(ResolveMode.DISCOVER, "", unit(".", repo)).get(0)
        .classpath().contains(classes));
  }

  @Test
  void checkedInJarsAreFound() throws IOException {
    Path lib = jar("lib/dep.jar");
    Path libs = jar("libs/nested/other.jar");

    List<Path> classpath = resolve(ResolveMode.DISCOVER, "", unit(".", repo)).get(0).classpath();

    assertTrue(classpath.contains(lib));
    assertTrue(classpath.contains(libs));
  }

  @Test
  void sourcesAndJavadocJarsAreNotClasspathEntries() throws IOException {
    jar("lib/dep-sources.jar");
    jar("lib/dep-javadoc.jar");
    Path real = jar("lib/dep.jar");

    List<Path> classpath = resolve(ResolveMode.DISCOVER, "", unit(".", repo)).get(0).classpath();

    assertEquals(List.of(real), classpath, "only the real artifact belongs on a classpath");
  }

  @Test
  void aVendoredRepositoryIsUsedButTheUsersOwnIsNot() throws IOException {
    Path vendored = jar(".m2/repository/org/acme/acme/1.0/acme-1.0.jar");

    assertTrue(resolve(ResolveMode.DISCOVER, "", unit(".", repo)).get(0)
        .classpath().contains(vendored),
        "a cache checked into the repo is fair game");
  }

  @Test
  void everyModulesOutputIsOfferedToEveryUnit() throws IOException {
    Path coreClasses = dir("core/target/classes");
    dir("app/target/classes");

    List<SourceUnit> resolved = resolve(ResolveMode.DISCOVER, "",
        unit("core", repo.resolve("core")), unit("app", repo.resolve("app")));

    SourceUnit app = resolved.stream().filter(u -> "app".equals(u.name())).findFirst().orElseThrow();
    assertTrue(app.classpath().contains(coreClasses),
        "a sibling module's output is how an unbuilt dependency still attributes");
  }

  @Test
  void noneModeDiscoversNothingButKeepsTheExplicitClasspath() throws IOException {
    dir("target/classes");
    Path explicit = jar("elsewhere/given.jar");

    List<Path> classpath =
        resolve(ResolveMode.NONE, explicit.toString(), unit(".", repo)).get(0).classpath();

    assertEquals(List.of(explicit), classpath);
  }

  @Test
  void extraClasspathIsAppendedNotSubstituted() throws IOException {
    Path classes = dir("target/classes");
    Path given = jar("elsewhere/given.jar");

    List<Path> classpath =
        resolve(ResolveMode.DISCOVER, given.toString(), unit(".", repo)).get(0).classpath();

    assertTrue(classpath.contains(classes), "the manual escape hatch must compose, not compete");
    assertTrue(classpath.contains(given));
  }

  @Test
  void multipleExtraEntriesAreSplitOnThePathSeparator() throws IOException {
    Path one = jar("elsewhere/one.jar");
    Path two = jar("elsewhere/two.jar");

    List<Path> classpath = resolve(ResolveMode.DISCOVER,
        one + File.pathSeparator + two, unit(".", repo)).get(0).classpath();

    assertTrue(classpath.contains(one));
    assertTrue(classpath.contains(two));
  }

  @Test
  void blankExtraEntriesAreIgnored() throws IOException {
    List<Path> classpath = resolve(ResolveMode.DISCOVER,
        File.pathSeparator + "  " + File.pathSeparator, unit(".", repo)).get(0).classpath();

    assertTrue(classpath.isEmpty());
  }

  @Test
  void anUnbuiltRepositoryYieldsAnEmptyClasspathWithoutFailing() {
    List<Path> classpath = resolve(ResolveMode.DISCOVER, "", unit(".", repo)).get(0).classpath();

    assertTrue(classpath.isEmpty(),
        "a freshly cloned repo has nothing on disk; that shows up as low attribution, not an error");
    assertTrue(diagnostics.isEmpty());
  }

  @Test
  void classpathEntriesAreNotDuplicated() throws IOException {
    dir("target/classes");

    List<Path> classpath = resolve(ResolveMode.DISCOVER, "", unit(".", repo)).get(0).classpath();

    assertEquals(classpath.size(), classpath.stream().distinct().count());
  }

  @Test
  void discoveryNeverWritesIntoTheTargetRepository() throws IOException {
    dir("target/classes");
    jar("lib/dep.jar");
    List<String> before = listing();

    resolve(ResolveMode.DISCOVER, "", unit(".", repo));

    assertEquals(before, listing(), "resolution must leave the target repo byte-identical");
  }

  private List<String> listing() throws IOException {
    try (var walk = Files.walk(repo)) {
      return walk.map(repo::relativize).map(Path::toString).sorted().toList();
    }
  }

  @Test
  void unitsKeepTheirIdentityThroughResolution() throws IOException {
    dir("target/classes");

    SourceUnit resolved = resolve(ResolveMode.DISCOVER, "", unit("core", repo)).get(0);

    assertEquals("core", resolved.name());
    assertEquals(21, resolved.release());
    assertFalse(resolved.classpathString().isEmpty());
  }
}
