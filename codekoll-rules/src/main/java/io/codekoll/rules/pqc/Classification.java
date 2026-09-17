package io.codekoll.rules.pqc;

/** Quantum-safety verdict for one algorithm name under one request type. */
sealed interface Classification
    permits Classification.Vulnerable, Classification.PostQuantum, Classification.NotAsymmetric {

  /** Breakable by a large quantum computer (Shor's algorithm). */
  record Vulnerable(Family family, Role role, String canonicalName) implements Classification {}

  /** A NIST post-quantum or hash-based algorithm. */
  record PostQuantum(Family family) implements Classification {}

  /** Symmetric, hash, MAC or password-based: outside this pack's threat. */
  record NotAsymmetric() implements Classification {}
}
