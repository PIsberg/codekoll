/** Codekoll CLI front-end. */
module io.codekoll.cli {
  requires io.codekoll.engine;
  requires io.codekoll.report;
  requires io.codekoll.workspace;
  requires info.picocli;
  requires static org.jspecify;

  exports io.codekoll.cli to info.picocli;
  opens io.codekoll.cli to info.picocli;
}
