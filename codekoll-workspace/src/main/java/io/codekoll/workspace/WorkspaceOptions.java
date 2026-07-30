package io.codekoll.workspace;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Knobs for discovery. Every field has a defensible default so that {@code codekoll} with no
 * flags at all does the right thing in a repository root.
 *
 * @param repoRoot explicit repo root, or {@code null} to detect one
 * @param includes extra source globs; when non-empty these replace layout-based discovery
 * @param excludes source globs to drop, applied after the built-in exclusions
 * @param includeTests whether test source roots are analyzed
 * @param useGitignore whether {@code .gitignore} entries are honoured
 * @param releaseOverride explicit {@code --release}, or {@code 0} to detect per unit
 * @param resolve classpath strategy
 * @param extraClasspath appended to every unit's resolved classpath; empty for none
 * @param maxFileBytes files larger than this are skipped as generated
 */
public record WorkspaceOptions(
    @Nullable Path repoRoot,
    List<String> includes,
    List<String> excludes,
    boolean includeTests,
    boolean useGitignore,
    int releaseOverride,
    ResolveMode resolve,
    String extraClasspath,
    long maxFileBytes) {

  /** Files above this size are generated parsers and lexers in practice, not hand-written code. */
  public static final long DEFAULT_MAX_FILE_BYTES = 2L * 1024 * 1024;

  public WorkspaceOptions {
    includes = List.copyOf(includes);
    excludes = List.copyOf(excludes);
  }

  /** The no-flags configuration. */
  public static WorkspaceOptions defaults() {
    return new WorkspaceOptions(null, List.of(), List.of(), true, true, 0,
        ResolveMode.DISCOVER, "", DEFAULT_MAX_FILE_BYTES);
  }
}
