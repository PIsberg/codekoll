package io.codekoll.rules.pqc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PqcGuidanceTest {

  private static final String BUILT_IN_KEM =
      "built into this project's Java platform (KEM.getInstance(\"ML-KEM\"))";
  private static final String BUILT_IN_DSA =
      "built into this project's Java platform (Signature.getInstance(\"ML-DSA\"))";
  private static final String NOT_BUILT_IN =
      "not built into this project's Java release: use Bouncy Castle PQC or target Java 24+";

  @Test
  void keyEstablishmentMessages() {
    assertEquals("ECDH key establishment is quantum-vulnerable: traffic recorded today can be decrypted "
        + "later. Replace with ML-KEM (FIPS 203), " + BUILT_IN_KEM
        + "; run it hybrid with ECDH until peers migrate.",
        PqcGuidance.keyEstablishment(List.of("ECDH"), true));
    assertEquals("x25519, secp256r1 key establishment is quantum-vulnerable: traffic recorded today can be "
        + "decrypted later. Replace with ML-KEM (FIPS 203), " + NOT_BUILT_IN
        + "; run it hybrid with x25519, secp256r1 until peers migrate.",
        PqcGuidance.keyEstablishment(List.of("x25519", "secp256r1"), false));
  }

  @Test
  void signatureMessages() {
    assertEquals("SHA256withECDSA signatures are quantum-vulnerable: a future quantum computer can forge "
        + "them. Replace with ML-DSA (FIPS 204), " + BUILT_IN_DSA + ".",
        PqcGuidance.signature(List.of("SHA256withECDSA"), true));
    assertEquals("Ed25519 signatures are quantum-vulnerable: a future quantum computer can forge them. "
        + "Replace with ML-DSA (FIPS 204), " + NOT_BUILT_IN + ".",
        PqcGuidance.signature(List.of("Ed25519"), false));
  }

  @Test
  void keyMaterialMessagesNameTheRoleWhenKnown() {
    assertEquals("X25519 key material is quantum-vulnerable (used for key establishment). "
        + "Plan its replacement with ML-KEM, " + BUILT_IN_KEM + ".",
        PqcGuidance.keyMaterial(List.of("X25519"), Role.KEY_ESTABLISHMENT, true));
    assertEquals("Ed448 key material is quantum-vulnerable (used for signatures). "
        + "Plan its replacement with ML-DSA, " + NOT_BUILT_IN + ".",
        PqcGuidance.keyMaterial(List.of("Ed448"), Role.SIGNATURE, false));
    assertEquals("EC key material is quantum-vulnerable (used for signatures or key establishment). "
        + "Plan its replacement with ML-KEM or ML-DSA, built into this project's Java platform "
        + "(KEM.getInstance(\"ML-KEM\"), Signature.getInstance(\"ML-DSA\")).",
        PqcGuidance.keyMaterial(List.of("EC"), Role.KEY_MATERIAL, true));
  }
}
