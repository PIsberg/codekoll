package io.codekoll.rules.pqc;

/** What a vulnerable algorithm is used for, which decides urgency and the replacement. */
enum Role {
  /** Confidentiality: traffic recorded today can be decrypted later. Replaced by ML-KEM. */
  KEY_ESTABLISHMENT,
  /** Integrity and authenticity at verification time. Replaced by ML-DSA. */
  SIGNATURE,
  /** A key or parameters whose use is decided elsewhere (RSA and EC keys serve both roles). */
  KEY_MATERIAL
}
