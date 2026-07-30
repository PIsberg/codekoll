package io.codekoll.workspace;

import java.nio.file.Path;
import java.util.List;

/**
 * One compilation scope in the target repository: a set of {@code .java} files that share a
 * language level and a classpath.
 *
 * <p>A Maven or Gradle module contributes one source unit, carrying both its main and its test
 * source roots. Keeping them together is deliberate: test code references main code, and a
 * single {@code javac} invocation resolves that without needing the module to have been built.
 *
 * @param name unit name, the module directory relative to the repo root ({@code .} for the root)
 * @param moduleDir directory holding the unit's build file, or the source root's parent
 * @param buildSystem how this unit's module declares its build
 * @param sourceRoots source roots contributing files (main first, then tests)
 * @param files the {@code .java} files to analyze, sorted for determinism
 * @param release the {@code --release} level to compile at
 * @param releaseDetected whether {@code release} was read from a build file rather than guessed
 * @param classpath resolved classpath entries (may be empty)
 */
public record SourceUnit(
    String name,
    Path moduleDir,
    BuildSystem buildSystem,
    List<Path> sourceRoots,
    List<Path> files,
    int release,
    boolean releaseDetected,
    List<Path> classpath) {

  public SourceUnit {
    // Defensive copies: record components must not alias caller-mutable collections
    // (codekoll's own CK-RECORD-MUTABLE-COMPONENT rule, and SpotBugs EI_EXPOSE_REP).
    sourceRoots = List.copyOf(sourceRoots);
    files = List.copyOf(files);
    classpath = List.copyOf(classpath);
  }

  /** Returns a copy of this unit with the given classpath. */
  public SourceUnit withClasspath(List<Path> resolved) {
    return new SourceUnit(name, moduleDir, buildSystem, sourceRoots, files, release,
        releaseDetected, resolved);
  }

  /** Returns the classpath joined with the platform separator, empty when there is none. */
  public String classpathString() {
    StringBuilder sb = new StringBuilder(classpath.size() * 40);
    for (Path entry : classpath) {
      if (sb.length() > 0) {
        sb.append(java.io.File.pathSeparatorChar);
      }
      sb.append(entry);
    }
    return sb.toString();
  }
}
