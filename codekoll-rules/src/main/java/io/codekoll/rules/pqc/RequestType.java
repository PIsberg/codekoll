package io.codekoll.rules.pqc;

/** The platform API a request site calls; decides which catalog section applies and which rule reports. */
enum RequestType {
  SIGNATURE,
  KEY_AGREEMENT,
  KEM,
  CIPHER,
  KEY_PAIR_GENERATOR,
  KEY_FACTORY,
  ALGORITHM_PARAMETERS,
  ALGORITHM_PARAMETER_GENERATOR,
  PARAMETER_SPEC,
  TLS_NAMED_GROUP,
  TLS_CIPHER_SUITE,
  TLS_SIGNATURE_SCHEME,
  XML_SIGNATURE_METHOD,
  /** A JOSE/JWT library's signature algorithm constant, for example {@code RS256}. */
  JOSE_SIGNATURE,
  /** A JOSE/JWT library's key-management algorithm constant, for example {@code RSA-OAEP-256}. */
  JOSE_KEY_MANAGEMENT
}
