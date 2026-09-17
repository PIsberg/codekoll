/**
 * Codekoll workspace: discovers what to analyze in a target repository — repo root, build
 * system, source units, per-unit language level and classpath.
 *
 * <p>Depends on nothing but the JDK's XML parser. In particular it does not require
 * {@code java.compiler}: discovery must never need the analyzer, and the analyzer must never
 * need discovery (the CLI wires the two together).
 */
module io.codekoll.workspace {
  requires java.xml;
  requires static org.jspecify;

  exports io.codekoll.workspace;
}
