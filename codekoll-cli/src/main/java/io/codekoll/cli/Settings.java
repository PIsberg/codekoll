package io.codekoll.cli;

import io.codekoll.api.Rule;
import io.codekoll.api.Severity;
import io.codekoll.workspace.ConfigException;
import io.codekoll.workspace.Configuration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * One effective value per setting, from the configuration file unless the command line said
 * otherwise (CLI-SPEC §10 resolution order).
 *
 * <p>Everything a flag can express, the config file can express too, so the merge lives in one
 * place rather than at each use site — a setting that honours the file in one code path and
 * ignores it in another is the bug this class exists to make impossible.
 *
 * <p>Keys that the schema defines but no milestone implements yet are reported, not ignored.
 * Silently accepting {@code report.baseline} would tell a user their findings are being filtered
 * against a baseline that is never read.
 */
final class Settings {

  /** Schema keys accepted by the parser but not yet acted on, with where they land. */
  private static final Map<String, String> NOT_YET_APPLIED = Map.of(
      "report.min-attribution", "Milestone 14",
      "report.baseline", "Milestone 15",
      "resolve.timeout", "Milestone 13",
      "rules.rule-path", "Milestone 12's second half");

  private final Configuration config;
  private final List<String> notes = new ArrayList<>();

  Settings(Configuration config) {
    this.config = config;
    for (Map.Entry<String, String> pending : NOT_YET_APPLIED.entrySet()) {
      config.originOf(pending.getKey()).ifPresent(origin -> notes.add(
          pending.getKey() + " (" + origin + ") is not applied yet: it arrives in "
              + pending.getValue()));
    }
  }

  /** Notes worth printing to stderr: settings that were read but do nothing yet. */
  List<String> notes() {
    return List.copyOf(notes);
  }

  String string(String key, @Nullable String cli, String fallback) {
    if (cli != null) {
      return cli;
    }
    return config.string(key).orElse(fallback);
  }

  boolean flag(String key, @Nullable Boolean cli, boolean fallback) {
    if (cli != null) {
      return cli;
    }
    return config.bool(key).orElse(fallback);
  }

  int integer(String key, @Nullable Integer cli, int fallback) {
    if (cli != null) {
      return cli;
    }
    return config.integer(key).orElse(fallback);
  }

  /** CLI list wins whole, rather than merging: {@code --packs security} means only security. */
  List<String> list(String key, List<String> cli) {
    return cli.isEmpty() ? config.strings(key) : cli;
  }

  /** The union of a CLI list and a config list, for settings that accumulate rather than replace. */
  List<String> union(String key, List<String> cli) {
    Set<String> all = new LinkedHashSet<>(config.strings(key));
    all.addAll(cli);
    return List.copyOf(all);
  }

  Optional<String> originOf(String key) {
    return config.originOf(key);
  }

  Map<String, Configuration.Value> values() {
    return config.values();
  }

  /**
   * Selects the rules to run: {@code enable-only} is an allowlist, {@code disable} and
   * {@code disable-packs} subtract, and every id named anywhere must exist.
   *
   * <p>An unknown id is an error rather than a no-op because the two are indistinguishable in the
   * output: a misspelled entry in {@code disable} leaves the rule running, and the person who
   * wrote it believes it is off.
   */
  List<Rule> select(List<Rule> rules, Set<String> cliIds, Set<String> cliPacks) {
    Set<String> knownIds = new LinkedHashSet<>();
    Set<String> knownPacks = new LinkedHashSet<>();
    for (Rule rule : rules) {
      knownIds.add(rule.id().value());
      knownPacks.add(rule.pack().id());
    }

    List<String> enableOnlyNamed = list("rules.enable-only", List.copyOf(cliIds));
    List<String> disableNamed = config.strings("rules.disable");
    List<String> disablePacksNamed = config.strings("rules.disable-packs");
    checkKnown(enableOnlyNamed, knownIds, "rule id", "rules.enable-only");
    checkKnown(disableNamed, knownIds, "rule id", "rules.disable");
    checkKnown(disablePacksNamed, knownPacks, "pack", "rules.disable-packs");
    checkKnown(List.copyOf(cliPacks), knownPacks, "pack", "--packs");

    // Sets, not the lists: this loop runs over every rule in the registry, and codekoll's own
    // CK-CONTAINS-IN-LOOP caught the list scans here when it analyzed this file.
    Set<String> enableOnly = new LinkedHashSet<>(enableOnlyNamed);
    Set<String> disable = new LinkedHashSet<>(disableNamed);
    Set<String> disablePacks = new LinkedHashSet<>(disablePacksNamed);

    List<Rule> selected = new ArrayList<>();
    for (Rule rule : rules) {
      String id = rule.id().value();
      String pack = rule.pack().id();
      boolean allowed = enableOnly.isEmpty() || enableOnly.contains(id);
      boolean dropped = disable.contains(id) || disablePacks.contains(pack)
          || !cliPacks.isEmpty() && !cliPacks.contains(pack);
      if (allowed && !dropped) {
        selected.add(rule);
      }
    }
    return selected;
  }

  /**
   * Severity overrides from {@code [severity]}, validated against the registry and the three
   * levels. Applied before reporting <em>and</em> before the exit code is computed, so raising a
   * rule to {@code error} actually fails a build.
   */
  Map<String, Severity> severityOverrides(List<Rule> allRules) {
    Set<String> knownIds = new LinkedHashSet<>();
    allRules.forEach(rule -> knownIds.add(rule.id().value()));

    Map<String, Severity> overrides = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : config.table("severity").entrySet()) {
      String id = entry.getKey();
      if (!knownIds.contains(id)) {
        throw new ConfigException("[severity] names an unknown rule id '" + id + "'"
            + originSuffix("severity.\"" + id + "\""));
      }
      overrides.put(id, severity(entry.getValue(), id));
    }
    return overrides;
  }

  /** {@code level} arrives already normalized by {@link Configuration#table}; folding it twice
   * would be both redundant and, on a locale-sensitive mapping, a way to spell around the set. */
  private Severity severity(String level, String id) {
    return switch (level) {
      case "error" -> Severity.ERROR;
      case "warning" -> Severity.WARNING;
      case "info" -> Severity.INFO;
      default -> throw new ConfigException("[severity] \"" + id + "\" = \"" + level
          + "\" is not a severity: expected error, warning or info");
    };
  }

  private void checkKnown(List<String> named, Set<String> known, String what, String where) {
    for (String name : named) {
      if (!known.contains(name)) {
        throw new ConfigException(where + " names an unknown " + what + " '" + name + "'"
            + originSuffix(where));
      }
    }
  }

  private String originSuffix(String key) {
    return config.originOf(key).map(origin -> " (" + origin + ")").orElse("");
  }
}
