/**
 * Built-in codekoll rule packs. Exports nothing: rules are reached only through the
 * {@link io.codekoll.api.Rule} service.
 */
module io.codekoll.rules {
  requires io.codekoll.api;
  requires static org.jspecify;

  provides io.codekoll.api.Rule with
      io.codekoll.rules.resources.EmptyCatchRule,
      io.codekoll.rules.concurrency.ThreadRunRule,
      io.codekoll.rules.security.WeakCryptoRule;
}
