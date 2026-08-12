package io.codekoll.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Works out what to analyze in a target repository (CLI-SPEC §3).
 *
 * <p>Discovery is layout-first and build-file-second, in that order on purpose. The directory
 * layout is a fact on disk; a build file is a claim that may be wrong, unparseable, or written in
 * a language codekoll refuses to execute. So the layout decides <em>which files</em> get
 * analyzed, and build files only refine <em>how</em> — the language level, mostly. When a build
 * file cannot be read, discovery loses precision and says so; it never loses files.
 */
public final class WorkspaceDiscovery {

  /** {@code .../src/<sourceSet>/java}, the layout Maven and Gradle both use. */
  private static final Pattern SOURCE_ROOT =
      Pattern.compile(".*/src/([^/]+)/java$");

  /** {@code .../src/<sourceSet>/resources}: data, not code, whatever it contains. */
  private static final Pattern RESOURCE_ROOT =
      Pattern.compile(".*/src/[^/]+/resources$");

  /** Source-set names whose contents are tests. */
  private static final Set<String> TEST_SOURCE_SETS =
      Set.of("test", "tests", "integrationtest", "integrationtests", "inttest",
          "testfixtures", "jmh", "it");

  private static final String JAVA_VERSION_FILE = ".java-version";

  private static final List<String> VERSION_FILES =
      List.of(JAVA_VERSION_FILE, ".sdkmanrc", ".tool-versions");

  /** A feature version, optionally with a minor part: {@code 21}, {@code 1.8}, {@code 17.0.2}. */
  private static final Pattern VERSION_NUMBER = Pattern.compile("(\\d+)(?:\\.(\\d+))?");

  /**
   * javac on a current JDK will not accept a {@code --release} below this. Clamping is better
   * than passing a value that makes every file in the unit fail to compile.
   */
  private static final int MIN_RELEASE = 8;

  private final WorkspaceOptions options;
  private final List<String> diagnostics = new ArrayList<>();

  public WorkspaceDiscovery(WorkspaceOptions options) {
    this.options = options;
  }

  /**
   * Discovers the workspace for the given CLI paths.
   *
   * @param paths files or directories named on the command line; empty means the working directory
   * @return the discovered workspace, never {@code null}, possibly with zero units
   */
  public Workspace discover(List<Path> paths) {
    List<Path> scanRoots = normalize(paths);
    Path explicitRoot = options.repoRoot();
    Path repoRoot = explicitRoot != null
        ? explicitRoot.toAbsolutePath().normalize()
        : detectRepoRoot(scanRoots);

    GitIgnoreMatcher gitignore = options.useGitignore()
        ? GitIgnoreMatcher.forRepo(repoRoot, diagnostics)
        : GitIgnoreMatcher.none();
    FileSelector selector = new FileSelector(repoRoot, options, gitignore, diagnostics);

    // A named file that is not Java source reaches javac as a compilation unit and throws
    // IllegalArgumentException there. `codekoll pom.xml` is a plausible typo, and a shell that
    // expands a glob into the positional list produces the same thing without anyone typing it.
    List<Path> named = scanRoots.stream().filter(Files::isRegularFile).toList();
    List<Path> explicitFiles = named.stream().filter(WorkspaceDiscovery::isJavaSource).toList();
    for (Path other : named) {
      if (!isJavaSource(other)) {
        diagnostics.add("not Java source, ignored: " + other);
      }
    }
    List<Path> dirs = scanRoots.stream().filter(Files::isDirectory).toList();
    for (Path missing : scanRoots) {
      if (!Files.exists(missing)) {
        diagnostics.add("path does not exist: " + missing);
      }
    }

    Map<Path, List<SourceRoot>> byModule = new LinkedHashMap<>();
    for (Path dir : dirs) {
      collectSourceRoots(dir, byModule);
    }

    List<SourceUnit> units = new ArrayList<>();
    BuildSystem repoBuildSystem = classify(repoRoot, byModule.isEmpty());

    if (!explicitFiles.isEmpty()) {
      units.add(explicitUnit(repoRoot, explicitFiles, repoBuildSystem));
    }
    if (byModule.isEmpty()) {
      units.addAll(plainUnits(repoRoot, dirs, selector));
    } else {
      units.addAll(layoutUnits(repoRoot, byModule, selector));
    }

    units.removeIf(unit -> unit.files().isEmpty());
    units.sort(Comparator.comparing(SourceUnit::name));

    Set<Path> sourcePath = new LinkedHashSet<>();
    for (SourceUnit unit : units) {
      sourcePath.addAll(unit.sourceRoots());
    }

    List<SourceUnit> resolved =
        new ClasspathResolver(repoRoot, diagnostics)
            .resolve(units, options.resolve(), options.extraClasspath());

    return new Workspace(repoRoot, repoBuildSystem, resolved,
        List.copyOf(sourcePath), List.copyOf(diagnostics));
  }

  private List<Path> normalize(List<Path> paths) {
    List<Path> roots = paths.isEmpty()
        ? List.of(Path.of("").toAbsolutePath())
        : paths;
    return roots.stream().map(p -> p.toAbsolutePath().normalize()).distinct().toList();
  }

  /**
   * Detects the repo root without discovering anything else.
   *
   * <p>Configuration lookup needs the root before discovery can run, because the repository's own
   * {@code codekoll.toml} is one of the things that decides what discovery does. Detection is a
   * handful of file-existence checks, so doing it twice costs nothing, and the diagnostics of the
   * real run are the ones reported.
   *
   * @param paths files or directories named on the command line; empty means the working directory
   * @param explicit {@code --repo}, or {@code null} to detect
   * @return the repo root, absolute and normalized
   */
  public static Path repoRootFor(List<Path> paths, @Nullable Path explicit) {
    if (explicit != null) {
      return explicit.toAbsolutePath().normalize();
    }
    WorkspaceDiscovery discovery = new WorkspaceDiscovery(WorkspaceOptions.defaults());
    return discovery.detectRepoRoot(discovery.normalize(paths));
  }
  /** CLI-SPEC §3.1: explicit root, then {@code .git}, then a build file, then the common prefix. */
  private Path detectRepoRoot(List<Path> scanRoots) {
    Path start = commonPrefix(scanRoots);
    for (Path dir = start; dir != null; dir = dir.getParent()) {
      if (Files.isDirectory(dir.resolve(".git")) || Files.isRegularFile(dir.resolve(".git"))) {
        return dir;
      }
    }
    for (Path dir = start; dir != null; dir = dir.getParent()) {
      if (BuildSystem.detectIn(dir) != null) {
        return dir;
      }
    }
    return start;
  }

  private static Path commonPrefix(List<Path> paths) {
    Path first = paths.get(0);
    Path prefix = Files.isDirectory(first) ? first : parentOr(first);
    for (Path path : paths.subList(1, paths.size())) {
      Path candidate = Files.isDirectory(path) ? path : parentOr(path);
      while (!candidate.startsWith(prefix)) {
        Path parent = prefix.getParent();
        if (parent == null) {
          return prefix;
        }
        prefix = parent;
      }
    }
    return prefix;
  }

  private static Path parentOr(Path path) {
    Path parent = path.getParent();
    return parent == null ? path : parent;
  }

  private BuildSystem classify(Path repoRoot, boolean noLayoutRoots) {
    BuildSystem declared = BuildSystem.detectIn(repoRoot);
    if (declared != null) {
      return declared;
    }
    if (noLayoutRoots) {
      return BuildSystem.PLAIN;
    }
    return BuildSystem.CONVENTIONAL;
  }

  /** Walks a directory tree collecting {@code src/<set>/java} roots, grouped by module. */
  private void collectSourceRoots(Path dir, Map<Path, List<SourceRoot>> byModule) {
    if (isSourceRoot(dir)) {
      record(dir, byModule);
      return;
    }
    try {
      Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path candidate, BasicFileAttributes attrs) {
          Path name = candidate.getFileName();
          if (name != null && FileSelector.isExcludedDirName(name.toString())) {
            return FileVisitResult.SKIP_SUBTREE;
          }
          if (isResourceDir(candidate)) {
            return FileVisitResult.SKIP_SUBTREE;
          }
          if (attrs.isSymbolicLink()) {
            return FileVisitResult.SKIP_SUBTREE;
          }
          if (isSourceRoot(candidate)) {
            record(candidate, byModule);
            return FileVisitResult.SKIP_SUBTREE;
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      diagnostics.add("could not scan " + dir + " for source roots: " + e.getMessage());
    }
  }

  private void record(Path sourceRoot, Map<Path, List<SourceRoot>> byModule) {
    String sourceSet = sourceSetOf(sourceRoot);
    boolean tests = TEST_SOURCE_SETS.contains(sourceSet.toLowerCase(Locale.ROOT));
    if (tests && !options.includeTests()) {
      return;
    }
    Path moduleDir = moduleDirOf(sourceRoot);
    if (moduleDir == null) {
      // isSourceRoot() already guarantees this; kept as a diagnostic so that a caller which
      // forgets that guard degrades visibly instead of throwing.
      diagnostics.add("ignoring source root with no module directory above it: " + sourceRoot);
      return;
    }
    List<SourceRoot> roots = byModule.computeIfAbsent(moduleDir, k -> new ArrayList<>());
    // Overlapping command-line paths (`codekoll . src/main/java`) reach the same root twice;
    // recording it twice analyzes every file in it twice and reports every finding twice.
    if (roots.stream().noneMatch(existing -> existing.path().equals(sourceRoot))) {
      roots.add(new SourceRoot(sourceRoot, tests));
    }
  }

  /** Java source, and not the {@code module-info.java} the engine never analyzes. */
  private static boolean isJavaSource(Path file) {
    Path name = file.getFileName();
    return name != null
        && name.toString().endsWith(".java")
        && !"module-info.java".equals(name.toString());
  }

  private static boolean isSourceRoot(Path dir) {
    return SOURCE_ROOT.matcher(dir.toString().replace('\\', '/')).matches()
        && moduleDirOf(dir) != null;
  }

  /**
   * True for a {@code src/<set>/resources} directory.
   *
   * <p>Resources are data, never compiled, and a project that ships sample repositories as test
   * data — codekoll itself does — has complete {@code src/main/java} layouts sitting under one.
   * Walking into them turns fixtures into modules and their sample bugs into findings against the
   * project that merely stores them.
   */
  private static boolean isResourceDir(Path dir) {
    return RESOURCE_ROOT.matcher(dir.toString().replace('\\', '/')).matches();
  }

  /**
   * Walks up from a {@code .../<module>/src/<set>/java} source root to {@code <module>}.
   *
   * @param sourceRoot the source root to walk up from
   * @return the module directory, or {@code null} if the path is too shallow to have one
   */
  private static @Nullable Path moduleDirOf(Path sourceRoot) {
    Path sourceSetDir = sourceRoot.getParent();
    if (sourceSetDir == null) {
      return null;
    }
    Path srcDir = sourceSetDir.getParent();
    if (srcDir == null) {
      return null;
    }
    return srcDir.getParent();
  }

  private static String sourceSetOf(Path sourceRoot) {
    Matcher matcher = SOURCE_ROOT.matcher(sourceRoot.toString().replace('\\', '/'));
    return matcher.matches() ? matcher.group(1) : "main";
  }

  private List<SourceUnit> layoutUnits(Path repoRoot, Map<Path, List<SourceRoot>> byModule,
      FileSelector selector) {
    List<SourceUnit> units = new ArrayList<>(byModule.size());
    for (Map.Entry<Path, List<SourceRoot>> entry : byModule.entrySet()) {
      Path moduleDir = entry.getKey();
      List<SourceRoot> roots = new ArrayList<>(entry.getValue());
      // Main sources first: javac resolves test references against them, and a deterministic
      // order keeps batching reproducible.
      roots.sort(Comparator.comparing(SourceRoot::tests).thenComparing(r -> r.path().toString()));

      List<Path> files = new ArrayList<>();
      for (SourceRoot root : roots) {
        files.addAll(selector.select(root.path()));
      }
      BuildSystem buildSystem = buildSystemOf(moduleDir);
      Release release = releaseFor(moduleDir, repoRoot, buildSystem);
      units.add(new SourceUnit(nameOf(repoRoot, moduleDir), moduleDir, buildSystem,
          roots.stream().map(SourceRoot::path).toList(), files,
          release.value(), release.detected(), List.of()));
    }
    return units;
  }

  private List<SourceUnit> plainUnits(Path repoRoot, List<Path> dirs, FileSelector selector) {
    List<SourceUnit> units = new ArrayList<>(dirs.size());
    for (Path dir : dirs) {
      List<Path> files = selector.select(dir);
      Release release = releaseFor(dir, repoRoot, buildSystemOf(dir));
      units.add(new SourceUnit(nameOf(repoRoot, dir), dir, BuildSystem.PLAIN,
          List.of(dir), files, release.value(), release.detected(), List.of()));
    }
    return units;
  }

  private SourceUnit explicitUnit(Path repoRoot, List<Path> files, BuildSystem buildSystem) {
    Release release = releaseFor(repoRoot, repoRoot, buildSystem);
    return new SourceUnit("<files>", repoRoot, buildSystem, List.of(), files,
        release.value(), release.detected(), List.of());
  }

  private static String nameOf(Path repoRoot, Path dir) {
    if (dir.equals(repoRoot)) {
      return ".";
    }
    Path relative = dir.startsWith(repoRoot) ? repoRoot.relativize(dir) : dir;
    return relative.toString().replace('\\', '/');
  }

  private static BuildSystem buildSystemOf(Path moduleDir) {
    BuildSystem declared = BuildSystem.detectIn(moduleDir);
    if (declared != null) {
      return declared;
    }
    return Files.isDirectory(moduleDir.resolve("src")) ? BuildSystem.CONVENTIONAL
        : BuildSystem.PLAIN;
  }

  /**
   * Determines the language level for a module, walking up to the repo root so that a child module
   * inherits what its parent declares.
   */
  private Release releaseFor(Path moduleDir, Path repoRoot, BuildSystem buildSystem) {
    if (options.releaseOverride() > 0) {
      return new Release(options.releaseOverride(), true);
    }
    for (Path dir = moduleDir; dir != null; dir = dir.getParent()) {
      Integer parsed = parse(declaredRelease(dir));
      if (parsed != null) {
        return new Release(clamp(parsed, dir), true);
      }
      if (dir.equals(repoRoot)) {
        break;
      }
    }
    Integer fromVersionFile = parse(versionFile(repoRoot));
    if (fromVersionFile != null) {
      return new Release(clamp(fromVersionFile, repoRoot), true);
    }
    int fallback = Runtime.version().feature();
    diagnostics.add("no language level declared for module '" + nameOf(repoRoot, moduleDir)
        + "' (" + buildSystem.label() + "); assuming --release " + fallback);
    return new Release(fallback, false);
  }

  private @Nullable String declaredRelease(Path dir) {
    PomReader pom = PomReader.read(dir, diagnostics);
    if (pom != null) {
      String release = pom.release();
      if (release != null) {
        return release;
      }
    }
    return GradleReader.release(dir);
  }

  private @Nullable String versionFile(Path repoRoot) {
    for (String name : VERSION_FILES) {
      Path file = repoRoot.resolve(name);
      if (!Files.isRegularFile(file)) {
        continue;
      }
      try {
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
          String line = raw.strip();
          if (line.isEmpty() || line.charAt(0) == '#') {
            continue;
          }
          // .java-version contains the version and nothing else, so there is no "java" token to
          // look for. .sdkmanrc and .tool-versions list several tools, so there the line must be
          // the Java one — reading another tool's version would be confidently wrong.
          if (!JAVA_VERSION_FILE.equals(name) && !line.toLowerCase(Locale.ROOT).contains("java")) {
            continue;
          }
          Matcher matcher = VERSION_NUMBER.matcher(line);
          if (matcher.find()) {
            return matcher.group(0);
          }
        }
      } catch (IOException e) {
        diagnostics.add("could not read " + name + ": " + e.getMessage());
      }
    }
    return null;
  }

  private int clamp(int release, Path source) {
    if (release < MIN_RELEASE) {
      diagnostics.add("language level " + release + " declared in " + source
          + " is below the minimum javac accepts; using --release " + MIN_RELEASE);
      return MIN_RELEASE;
    }
    int current = Runtime.version().feature();
    if (release > current) {
      diagnostics.add("language level " + release + " declared in " + source
          + " is newer than the running JDK; using --release " + current);
      return current;
    }
    return release;
  }

  /** Accepts {@code 21}, {@code 1.8} and {@code 17.0.2}. */
  private static @Nullable Integer parse(@Nullable String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    Matcher matcher = Pattern.compile("^(\\d+)(?:\\.(\\d+))?").matcher(text.strip());
    if (!matcher.find()) {
      return null;
    }
    int major = Integer.parseInt(matcher.group(1));
    if (major == 1 && matcher.group(2) != null) {
      return Integer.parseInt(matcher.group(2));
    }
    return major;
  }

  private record SourceRoot(Path path, boolean tests) {}

  private record Release(int value, boolean detected) {}
}
