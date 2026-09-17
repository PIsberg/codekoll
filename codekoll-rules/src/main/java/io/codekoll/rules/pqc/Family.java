package io.codekoll.rules.pqc;

/** Algorithm family a requested name belongs to, classical or post-quantum. */
enum Family {
  RSA,
  RSASSA_PSS,
  DSA,
  EC,
  EDDSA,
  DH,
  XDH,
  DHKEM,
  HPKE,
  ML_KEM,
  ML_DSA,
  SLH_DSA,
  HSS_LMS
}
