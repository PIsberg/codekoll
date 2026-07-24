package io.codekoll.engine;

import io.codekoll.api.Rule;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/** Discovers rules via {@link ServiceLoader} and filters them by id / pack. */
public final class RuleRegistry {

  private RuleRegistry() {}

  /** All rules on the class/module path, sorted by id for deterministic output. */
  public static List<Rule> loadAll() {
    return ServiceLoader.load(Rule.class).stream()
        .map(ServiceLoader.Provider::get)
        .sorted(Comparator.comparing(r -> r.id().value()))
        .collect(Collectors.toList());
  }

  /** Restricts {@code rules} to the given rule ids (empty set = no restriction). */
  public static List<Rule> filterByIds(List<Rule> rules, Set<String> ids) {
    if (ids.isEmpty()) {
      return rules;
    }
    return rules.stream().filter(r -> ids.contains(r.id().value())).collect(Collectors.toList());
  }

  /** Restricts {@code rules} to the given pack ids (empty set = no restriction). */
  public static List<Rule> filterByPacks(List<Rule> rules, Set<String> packs) {
    if (packs.isEmpty()) {
      return rules;
    }
    return rules.stream().filter(r -> packs.contains(r.pack().id())).collect(Collectors.toList());
  }
}
