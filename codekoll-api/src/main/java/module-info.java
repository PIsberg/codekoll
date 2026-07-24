/**
 * Codekoll rule SPI. Zero dependencies beyond the JDK; {@code jdk.compiler} is required
 * transitively because {@link io.codekoll.api.Rule#scan} exposes attributed compiler trees.
 */
module io.codekoll.api {
  requires transitive jdk.compiler;
  requires static org.jspecify;

  exports io.codekoll.api;
}
