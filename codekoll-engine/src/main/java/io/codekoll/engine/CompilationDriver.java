package io.codekoll.engine;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import io.codekoll.api.Finding;
import io.codekoll.api.Rule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Runs the system {@code javac} over source files up to the {@code analyze} phase (fully
 * attributed trees) and dispatches every enabled rule over each compilation unit.
 *
 * <p>Files that fail attribution are reported as skipped, not analyzed. A rule that throws is
 * recorded as a rule failure and analysis continues.
 */
public final class CompilationDriver {

  private final List<String> options;

  /**
   * @param release the {@code --release} value passed to javac
   * @param classpath user classpath for type resolution of dependencies; empty for none
   */
  public CompilationDriver(int release, String classpath) {
    List<String> opts = new ArrayList<>(List.of("--release", Integer.toString(release),
        "-proc:none", "-nowarn"));
    if (!classpath.isEmpty()) {
      opts.add("-classpath");
      opts.add(classpath);
    }
    this.options = List.copyOf(opts);
  }

  /** Collects {@code .java} files under the given roots (files are taken as-is) and analyzes. */
  public AnalysisResult analyzePaths(List<Path> roots, List<Rule> rules) {
    List<Path> sources = new ArrayList<>();
    for (Path root : roots) {
      if (Files.isRegularFile(root)) {
        sources.add(root);
      } else {
        try (Stream<Path> walk = Files.walk(root)) {
          walk.filter(p -> p.toString().endsWith(".java"))
              .filter(p -> {
                Path name = p.getFileName();
                return name != null && !"module-info.java".equals(name.toString());
              })
              .forEach(sources::add);
        } catch (IOException e) {
          throw new UncheckedIOException("Cannot walk " + root, e);
        }
      }
    }
    JavaCompiler compiler = systemCompiler();
    try (StandardJavaFileManager fm =
        compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(tempClassOutput()));
      Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(sources);
      List<JavaFileObject> list = new ArrayList<>();
      units.forEach(list::add);
      return analyze(compiler, fm, list, rules);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Analyzes pre-built file objects (used by the fixture test harness). */
  public AnalysisResult analyzeFileObjects(List<? extends JavaFileObject> files,
      List<Rule> rules) {
    JavaCompiler compiler = systemCompiler();
    try (StandardJavaFileManager fm =
        compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(tempClassOutput()));
      return analyze(compiler, fm, files, rules);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private AnalysisResult analyze(JavaCompiler compiler, StandardJavaFileManager fm,
      List<? extends JavaFileObject> files, List<Rule> rules) throws IOException {
    if (files.isEmpty()) {
      return new AnalysisResult(List.of(), Map.of(), List.of());
    }
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    JavacTask task = (JavacTask) compiler.getTask(
        null, fm, diagnostics, options, null, files);
    Iterable<? extends CompilationUnitTree> units = task.parse();
    task.analyze();

    Map<Path, String> skipped = new LinkedHashMap<>();
    Map<URI, String> errorsBySource = new HashMap<>();
    for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
      if (d.getKind() == Diagnostic.Kind.ERROR && d.getSource() != null) {
        errorsBySource.putIfAbsent(d.getSource().toUri(),
            d.getLineNumber() + ": " + d.getMessage(Locale.ROOT));
      }
    }

    Trees trees = Trees.instance(task);
    Types types = task.getTypes();
    Elements elements = task.getElements();

    List<Finding> findings = new ArrayList<>();
    List<String> ruleFailures = new ArrayList<>();
    for (CompilationUnitTree unit : units) {
      URI uri = unit.getSourceFile().toUri();
      Path path = pathOf(unit.getSourceFile());
      String error = errorsBySource.get(uri);
      if (error != null) {
        skipped.put(path, error);
        continue;
      }
      SuppressionFilter filter = SuppressionFilter.forUnit(unit);
      FindingSink sink = new FindingSink(findings, filter);
      for (Rule rule : rules) {
        scanGuarded(rule, unit, trees, types, elements, sink, path, ruleFailures);
      }
    }
    findings.sort(Comparator.comparing((Finding f) -> f.file().toString())
        .thenComparingLong(Finding::line)
        .thenComparingLong(Finding::column)
        .thenComparing(f -> f.rule().value()));
    return new AnalysisResult(List.copyOf(findings), skipped, List.copyOf(ruleFailures));
  }

  // Intentional broad catch: the engine's contract is that a crashing rule never aborts
  // the analysis of other rules/files — the crash is recorded and surfaced instead.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private static void scanGuarded(Rule rule, CompilationUnitTree unit, Trees trees, Types types,
      Elements elements, io.codekoll.api.FindingCollector sink, Path path,
      List<String> ruleFailures) {
    try {
      rule.scan(unit, trees, types, elements, sink);
    } catch (RuntimeException e) {
      ruleFailures.add(rule.id() + " crashed on " + path + ": " + e);
    }
  }

  static Path pathOf(JavaFileObject file) {
    URI uri = file.toUri();
    if ("file".equals(uri.getScheme())) {
      return Path.of(uri);
    }
    String name = file.getName();
    return Path.of(name.startsWith("/") ? name.substring(1) : name);
  }

  private static JavaCompiler systemCompiler() {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler: run codekoll on a JDK, not a JRE");
    }
    return compiler;
  }

  private static Path tempClassOutput() throws IOException {
    return Files.createTempDirectory("codekoll-classes");
  }

  /** Applies suppression, then appends to the shared list. */
  private record FindingSink(List<Finding> findings, SuppressionFilter filter)
      implements io.codekoll.api.FindingCollector {
    @Override
    public void report(Finding finding) {
      if (!filter.isSuppressed(finding)) {
        findings.add(finding);
      }
    }
  }
}
