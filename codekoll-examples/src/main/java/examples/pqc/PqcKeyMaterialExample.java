package examples.pqc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

/**
 * Example for rule {@code CK-PQC-KEY-MATERIAL}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} generates an RSA key pair, key material a large quantum
 * computer can break whatever it is later used for.
 *
 * <p><b>What happens at runtime:</b> the keys work today. Data encrypted to them can be recorded now
 * and decrypted later, and signatures made with them can be forged, once Shor's algorithm runs at
 * scale. Reported as info: the site that uses the key carries the warning.
 *
 * <p><b>How to fix it:</b> generate ML-KEM keys for key establishment, as {@link #fixed()} does, or
 * ML-DSA keys for signatures.
 */
public class PqcKeyMaterialExample {

  public KeyPair buggy() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); // :: CK-PQC-KEY-MATERIAL
    generator.initialize(3072);
    return generator.generateKeyPair();
  }

  public KeyPair fixed() throws Exception {
    return KeyPairGenerator.getInstance("ML-KEM-768").generateKeyPair();
  }
}
