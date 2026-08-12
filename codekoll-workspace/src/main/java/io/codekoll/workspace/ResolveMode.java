package io.codekoll.workspace;

import java.util.Locale;

/**
 * How the target repository's dependency classpath is obtained (CLI-SPEC §4.1).
 *
 * <p>{@link #BUILD} is not reachable by default and never from the target repository's own
 * configuration: invoking a repository's build tool executes that repository's code. See
 * CLI-SPEC §4.3 for the trust rules that gate it.
 */
public enum ResolveMode {
  /** Hermetic: existing build outputs and jars already on disk. No subprocess. */
  DISCOVER,
  /** Ask the target's own build tool for a classpath. Requires explicit opt-in. */
  BUILD,
  /** {@link #BUILD} if opt-in was granted, else {@link #DISCOVER}. */
  AUTO,
  /** Nothing is discovered; only an explicit {@code --classpath} is used. */
  NONE;

  /** Parses a CLI value, case-insensitively. */
  public static ResolveMode parse(String value) {
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "discover" -> DISCOVER;
      case "build" -> BUILD;
      case "auto" -> AUTO;
      case "none" -> NONE;
      default -> throw new IllegalArgumentException(
          "Unknown --resolve mode '" + value + "': expected discover, build, auto or none");
    };
  }
}
