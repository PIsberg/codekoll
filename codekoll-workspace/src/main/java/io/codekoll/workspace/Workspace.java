package io.codekoll.workspace;

import java.nio.file.Path;
import java.util.List;

/**
 * The result of discovery: what codekoll decided to analyze in a target repository, and what it
 * could not work out along the way.
 *
 * <p>{@code diagnostics} is not decoration. Every guess discovery makes — a build file it could
 * not parse, a language level it had to fall back on, a symlink cycle it cut — lands here and is
 * printed by {@code --print-workspace} and {@code --verbose}. Discovery failing silently is the
 * failure mode this whole module exists to prevent.
 *
 * @param repoRoot root that reported paths are relative to
 * @param buildSystem build system of the repo root itself
 * @param units compilation scopes, in discovery order
 * @param sourcePath every discovered source root, offered to javac as {@code -sourcepath} so that
 *     cross-module references attribute without the target having been built
 * @param diagnostics human-readable notes about anything discovery could not determine
 */
public record Workspace(
    Path repoRoot,
    BuildSystem buildSystem,
    List<SourceUnit> units,
    List<Path> sourcePath,
    List<String> diagnostics) {

  public Workspace {
    units = List.copyOf(units);
    sourcePath = List.copyOf(sourcePath);
    diagnostics = List.copyOf(diagnostics);
  }

  /** Total number of {@code .java} files selected across all units. */
  public int fileCount() {
    return units.stream().mapToInt(u -> u.files().size()).sum();
  }

  /**
   * Relativizes a path against the repo root, using {@code /} on every platform.
   *
   * <p>The repo root itself renders as {@code "."} rather than the empty string: an empty path in
   * a report reads as missing data, and {@code .} is what the unit naming already uses for the
   * root module. A path outside the root keeps its absolute form — silently rendering it as a
   * relative path would point at a file that is not there.
   */
  public String relativize(Path file) {
    Path target = file.toAbsolutePath().normalize();
    Path root = repoRoot.toAbsolutePath().normalize();
    String text = target.startsWith(root)
        ? root.relativize(target).toString()
        : target.toString();
    return text.isEmpty() ? "." : text.replace('\\', '/');
  }
}
