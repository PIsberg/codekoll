package io.codekoll.workspace;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Hermetic classpath discovery (CLI-SPEC §4.1, {@code --resolve discover}): everything usable
 * that is already on disk, and not one subprocess.
 *
 * <p>This is the strategy that runs by default, so it must be safe on a repository the user has
 * only just cloned and does not trust. It reads directories and notes jar files; it never
 * executes the target's build, never reaches the network, and never writes anything.
 *
 * <p>What it finds is real but partial: a repository that has never been built has no compiled
 * output to point at, and its dependencies live in a cache this strategy deliberately does not
 * rummage through. That gap is not hidden — it surfaces as a lower attribution percentage, which
 * is the honest signal that {@code --resolve build} is what the run actually needs.
 */
final class ClasspathResolver {

  /** Compiled-output directories, relative to a module directory. */
  private static final List<String> OUTPUT_DIRS = List.of(
      "target/classes", "target/test-classes",
      "build/classes/java/main", "build/classes/java/test",
      "build/classes/kotlin/main", "build/resources/main");

  /** Directories whose jars are dependencies checked into the repository. */
  private static final List<String> JAR_DIRS = List.of(
      "lib", "libs", "build/libs", "target/lib", "target/dependency");

  /** Guard against a vendored artifact cache turning into a 50 000-entry classpath. */
  private static final int MAX_VENDORED_JARS = 4000;
  private static final int JAR_SEARCH_DEPTH = 3;

  private final Path repoRoot;
  private final List<String> diagnostics;

  ClasspathResolver(Path repoRoot, List<String> diagnostics) {
    this.repoRoot = repoRoot;
    this.diagnostics = diagnostics;
  }

  /**
   * Resolves a classpath for every unit.
   *
   * <p>Every module's compiled output is offered to every unit. In a multi-module repository the
   * alternative is reconstructing the dependency graph from build files, which cannot be done
   * reliably without running the build — and being over-generous here costs nothing but a longer
   * classpath, while being too strict costs attribution.
   */
  List<SourceUnit> resolve(List<SourceUnit> units, ResolveMode mode, String extraClasspath) {
    List<Path> shared = new ArrayList<>();
    if (mode != ResolveMode.NONE) {
      Set<Path> outputs = new LinkedHashSet<>();
      for (SourceUnit unit : units) {
        outputs.addAll(outputDirs(unit.moduleDir()));
      }
      shared.addAll(outputs);
      shared.addAll(vendoredJars());
    }
    List<Path> extra = parse(extraClasspath);

    List<SourceUnit> resolved = new ArrayList<>(units.size());
    for (SourceUnit unit : units) {
      Set<Path> entries = new LinkedHashSet<>();
      if (mode != ResolveMode.NONE) {
        entries.addAll(outputDirs(unit.moduleDir()));
        entries.addAll(jarsUnder(unit.moduleDir()));
        entries.addAll(shared);
      }
      entries.addAll(extra);
      resolved.add(unit.withClasspath(List.copyOf(entries)));
    }
    return resolved;
  }

  private List<Path> outputDirs(Path moduleDir) {
    List<Path> found = new ArrayList<>();
    for (String relative : OUTPUT_DIRS) {
      Path candidate = moduleDir.resolve(relative.replace('/', File.separatorChar));
      if (Files.isDirectory(candidate)) {
        found.add(candidate);
      }
    }
    return found;
  }

  private List<Path> jarsUnder(Path moduleDir) {
    List<Path> found = new ArrayList<>();
    for (String relative : JAR_DIRS) {
      Path dir = moduleDir.resolve(relative.replace('/', File.separatorChar));
      if (Files.isDirectory(dir)) {
        found.addAll(jars(dir, JAR_SEARCH_DEPTH, Integer.MAX_VALUE));
      }
    }
    return found;
  }

  /**
   * Jars from an artifact cache vendored into the repository itself ({@code <repo>/.m2}), which
   * some projects check in for reproducible offline builds. A cache inside the repo is fair game;
   * the user's own {@code ~/.m2} is not, because picking versions out of it without reading the
   * dependency graph would put arbitrary, possibly wrong, artifacts on the classpath.
   */
  private List<Path> vendoredJars() {
    Path vendored = repoRoot.resolve(".m2").resolve("repository");
    if (!Files.isDirectory(vendored)) {
      return List.of();
    }
    List<Path> found = jars(vendored, Integer.MAX_VALUE, MAX_VENDORED_JARS);
    if (found.size() >= MAX_VENDORED_JARS) {
      diagnostics.add("vendored .m2/repository has more than " + MAX_VENDORED_JARS
          + " jars; only the first " + MAX_VENDORED_JARS + " are on the classpath");
    }
    return found;
  }

  private List<Path> jars(Path dir, int depth, int limit) {
    try (Stream<Path> walk = Files.walk(dir, depth)) {
      return walk.filter(Files::isRegularFile)
          .filter(p -> {
            Path name = p.getFileName();
            return name != null && name.toString().endsWith(".jar")
                && !name.toString().endsWith("-sources.jar")
                && !name.toString().endsWith("-javadoc.jar");
          })
          .sorted()
          .limit(limit)
          .toList();
    } catch (IOException e) {
      diagnostics.add("could not scan " + dir + " for jars: " + e.getMessage());
      return List.of();
    }
  }

  private static List<Path> parse(String classpath) {
    if (classpath.isEmpty()) {
      return List.of();
    }
    List<Path> entries = new ArrayList<>();
    for (String part : classpath.split(File.pathSeparator, -1)) {
      if (!part.isBlank()) {
        entries.add(Path.of(part.strip()));
      }
    }
    return entries;
  }
}
