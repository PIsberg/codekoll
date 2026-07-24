/**
 * Codekoll engine: drives {@code javac} to attributed ASTs and dispatches rules over them.
 * The only module allowed to create a {@code JavacTask}.
 */
module io.codekoll.engine {
  requires transitive io.codekoll.api;
  requires java.compiler;
  requires static org.jspecify;

  uses io.codekoll.api.Rule;

  exports io.codekoll.engine;
  exports io.codekoll.engine.testing;
}
