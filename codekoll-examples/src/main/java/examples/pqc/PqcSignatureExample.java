package examples.pqc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;

/**
 * Example for rule {@code CK-PQC-SIGNATURE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(PrivateKey, byte[])} signs with ECDSA, which a large quantum
 * computer breaks with Shor's algorithm.
 *
 * <p><b>What happens at runtime:</b> signing and verification work today. Once a quantum computer
 * exists, anyone holding the public key can derive the private key and forge signatures that every
 * verifier accepts, which matters most for long-lived signatures such as releases and certificates.
 *
 * <p><b>How to fix it:</b> sign with ML-DSA, as {@link #fixed(byte[])} does. Its keys and signatures
 * are several times larger and verifiers must support it, so plan dual signatures while they differ.
 */
public class PqcSignatureExample {

  public byte[] buggy(PrivateKey key, byte[] data) throws Exception {
    Signature signer = Signature.getInstance("SHA256withECDSA"); // :: CK-PQC-SIGNATURE
    signer.initSign(key);
    signer.update(data);
    return signer.sign();
  }

  public byte[] fixed(byte[] data) throws Exception {
    KeyPair keys = KeyPairGenerator.getInstance("ML-DSA").generateKeyPair();
    Signature signer = Signature.getInstance("ML-DSA");
    signer.initSign(keys.getPrivate());
    signer.update(data);
    return signer.sign();
  }
}
