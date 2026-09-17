package io.codekoll.engine;

import java.nio.file.Path;
import java.util.List;

/**
 * One compilation scope the engine can analyze: files that share a language level and a classpath.
 *
 * <p>The engine's own vocabulary, deliberately not {@code io.codekoll.workspace.SourceUnit}. The
 * engine must be usable without discovery, and discovery must be usable without the engine; the
 * CLI is the one component that knows about both and translates between them.
 *
 * @param name unit name for diagnostics
 * @param files the {@code .java} files to analyze
 * @param release the {@code --release} level to compile at
 * @param classpath platform-separated classpath, empty for none
 * @param sourcePath roots offered to javac for on-demand attribution of referenced sources; these
 *     files are resolved but never analyzed and never produce findings
 */
public record AnalysisUnit(
    String name,
    List<Path> files,
    int release,
    String classpath,
    List<Path> sourcePath) {

  public AnalysisUnit {
    files = List.copyOf(files);
    sourcePath = List.copyOf(sourcePath);
  }

  /** A unit with no classpath and no source path — what the simple single-directory run uses. */
  public static AnalysisUnit of(String name, List<Path> files, int release, String classpath) {
    return new AnalysisUnit(name, files, release, classpath, List.of());
  }

  /** Returns a copy of this unit carrying only the given files, for batching. */
  public AnalysisUnit withFiles(List<Path> batch) {
    return new AnalysisUnit(name, batch, release, classpath, sourcePath);
  }
}
