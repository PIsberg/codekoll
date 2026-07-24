/** Codekoll reporters. Depends on the API only — must never require jdk.compiler directly. */
module io.codekoll.report {
  requires io.codekoll.api;
  requires static org.jspecify;

  exports io.codekoll.report;
}
