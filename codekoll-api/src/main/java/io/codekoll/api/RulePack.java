package io.codekoll.api;

import java.util.Locale;

/** The pack a rule belongs to; packs can be enabled/disabled as a unit. */
public enum RulePack {
  CORRECTNESS,
  NUMERIC,
  CONCURRENCY,
  RESOURCES,
  SECURITY,
  PERFORMANCE,
  API_MISUSE,
  NULLNESS,
  MODERN,
  FRAMEWORKS;

  /** Config/CLI name, e.g. {@code api-misuse}. */
  public String id() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
