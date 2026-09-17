package io.codekoll.workspace;

import io.codekoll.workspace.TomlReader.Entry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Finds, validates and merges configuration files (CLI-SPEC §10), recording where every value
 * came from.
 *
 * <p>Two rules run through everything here. <em>Unknown is an error</em>: a misspelled key leaves
 * a setting silently unapplied, and a rule its author believed was disabled keeps firing.
 * <em>The target repository is untrusted input</em> (§14): its {@code codekoll.toml} was written
 * by whoever wrote the repository, who on a foreign repo is not the person running codekoll. It
 * may configure what to analyze; it may not enable build execution, load code from a path it
 * controls, or redirect output outside itself.
 */
public final class ConfigLoader {

  /** Config-file names looked for in the repo root, in order. */
  private static final List<String> REPO_CONFIG_NAMES =
      List.of("codekoll.toml", ".codekoll.toml", ".config/codekoll.toml");

  /** Every key the schema defines. Anything else is a typo, and a typo is an error. */
  private static final Set<String> KNOWN_KEYS = Set.of(
      "rules.disable", "rules.disable-packs", "rules.enable-only", "rules.rule-path",
      "suppress.paths",
      "sources.include", "sources.exclude", "sources.tests", "sources.gitignore",
      "compile.release", "compile.classpath",
      "resolve.mode", "resolve.timeout",
      "report.format", "report.fail-on", "report.min-attribution", "report.baseline",
      "report.output", "report.absolute-paths");

  /** Tables whose keys are user-chosen (rule ids) rather than fixed. */
  private static final Set<String> FREE_FORM_TABLES = Set.of("severity");

  /** Keys a repository may not set about the machine running codekoll (§14). */
  private static final Set<String> REPO_FORBIDDEN_KEYS = Set.of("rules.rule-path");

  /**
   * The only {@code resolve.mode} values a target repository may choose, matched exactly.
   *
   * <p>An allowlist rather than a blocklist of {@code build}/{@code auto}, and no case folding on
   * the way in: case mapping is locale- and Unicode-sensitive, which makes it the wrong tool for
   * a decision about whether to execute someone else's build. Exact strings cannot be spelled
   * around.
   */
  private static final Set<Object> REPO_RESOLVE_MODES = Set.of("discover", "none");

  private ConfigLoader() {}

  /**
   * Loads the effective configuration.
   *
   * @param repoRoot the target repository root; its config is treated as untrusted
   * @param explicit {@code --config <file>}, which replaces the repo config rather than merging
   *     with it, or {@code null}
   * @param userConfigDir the user's config directory (`$XDG_CONFIG_HOME` or `~/.config`), or
   *     {@code null} to skip user configuration entirely
   * @param diagnostics collects notes about files that were looked for and not found
   * @return the merged configuration
   * @throws ConfigException if a file is unreadable, malformed, unknown-keyed, or oversteps §14
   */
  public static Configuration load(Path repoRoot, @Nullable Path explicit,
      @Nullable Path userConfigDir, List<String> diagnostics) {
    Map<String, Configuration.Value> merged = new LinkedHashMap<>();

    if (userConfigDir != null) {
      Path userConfig = userConfigDir.resolve("codekoll").resolve("config.toml");
      if (Files.isRegularFile(userConfig)) {
        merge(merged, read(userConfig, "user config"), userConfig, repoRoot, false);
      }
    }

    if (explicit != null) {
      if (!Files.isRegularFile(explicit)) {
        throw new ConfigException("--config " + explicit + " does not exist");
      }
      // Trusted: the person running codekoll named this file on the command line.
      merge(merged, read(explicit, "--config"), explicit, repoRoot, false);
      return new Configuration(merged);
    }

    Path repoConfig = findRepoConfig(repoRoot);
    if (repoConfig == null) {
      diagnostics.add("no codekoll.toml in " + repoRoot + "; using defaults and CLI flags");
    } else {
      merge(merged, read(repoConfig, "repo config"), repoConfig, repoRoot, true);
    }
    return new Configuration(merged);
  }

  private static @Nullable Path findRepoConfig(Path repoRoot) {
    for (String name : REPO_CONFIG_NAMES) {
      Path candidate = repoRoot.resolve(name);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private static Map<String, Entry> read(Path file, String what) {
    try {
      return TomlReader.read(file);
    } catch (TomlReader.TomlException e) {
      throw new ConfigException(what + ": " + e.getMessage(), e);
    }
  }

  private static void merge(Map<String, Configuration.Value> target, Map<String, Entry> entries,
      Path file, Path repoRoot, boolean untrusted) {
    for (Entry entry : entries.values()) {
      validateKey(entry, file);
      if (untrusted) {
        enforceRepoLimits(entry, file, repoRoot);
      }
      String origin = originOf(file, entry);
      target.put(entry.key(), new Configuration.Value(entry.value(), origin));
    }
  }

  private static String originOf(Path file, Entry entry) {
    return file.getFileName() + ":" + entry.line();
  }

  private static void validateKey(Entry entry, Path file) {
    String key = entry.key();
    int dot = key.indexOf('.');
    if (dot < 0) {
      throw new ConfigException(where(file, entry)
          + "top-level keys are not part of the schema; put '" + key + "' under a table");
    }
    if (FREE_FORM_TABLES.contains(key.substring(0, dot))) {
      return;
    }
    if (!KNOWN_KEYS.contains(key)) {
      throw new ConfigException(where(file, entry) + "unknown key '" + key + "'"
          + suggestion(key));
    }
  }

  /**
   * §14: a repository describes itself, never the machine analyzing it. Each violation names the
   * key, because "config rejected" tells the person holding the repo nothing about what to change.
   */
  private static void enforceRepoLimits(Entry entry, Path file, Path repoRoot) {
    String key = entry.key();
    if (REPO_FORBIDDEN_KEYS.contains(key)) {
      throw new ConfigException(where(file, entry) + "'" + key
          + "' may not be set by the analyzed repository: it loads code from a path the "
          + "repository controls. Set it in your user config or on the command line.");
    }
    if ("resolve.mode".equals(key) && !REPO_RESOLVE_MODES.contains(entry.value())) {
      throw new ConfigException(where(file, entry) + "'resolve.mode = " + entry.value()
          + "' may not be set by the analyzed repository: the modes it may choose are "
          + "\"discover\" and \"none\", spelled exactly. Anything else can end in codekoll "
          + "running that repository's build tool; pass --resolve build yourself if you trust it.");
    }
    if ("report.output".equals(key) && escapesRoot(String.valueOf(entry.value()), repoRoot)) {
      throw new ConfigException(where(file, entry)
          + "'report.output' may not point outside the repository being analyzed: "
          + entry.value());
    }
  }

  private static boolean escapesRoot(String output, Path repoRoot) {
    Path target = repoRoot.resolve(output).toAbsolutePath().normalize();
    return !target.startsWith(repoRoot.toAbsolutePath().normalize());
  }

  private static String where(Path file, Entry entry) {
    return file.getFileName() + ":" + entry.line() + ": ";
  }

  /** Offers the closest schema key, which is usually the one that was meant. */
  private static String suggestion(String key) {
    String best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (String candidate : KNOWN_KEYS) {
      int distance = distance(key, candidate);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    return best != null && bestDistance <= 3 ? " (did you mean '" + best + "'?)" : "";
  }

  private static int distance(String a, String b) {
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
        current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }
}
