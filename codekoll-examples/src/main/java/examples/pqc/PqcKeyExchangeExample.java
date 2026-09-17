package examples.pqc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.KEM;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;

/**
 * Example for rule {@code CK-PQC-KEY-EXCHANGE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(PrivateKey, PublicKey)} derives a shared secret with X25519
 * key agreement, which a large quantum computer breaks with Shor's algorithm.
 *
 * <p><b>What happens at runtime:</b> it works today. An attacker who records the handshake can
 * store it and derive the same secret once a quantum computer exists, then decrypt everything that
 * secret protected (harvest now, decrypt later).
 *
 * <p><b>How to fix it:</b> establish the secret with ML-KEM, as {@link #fixed()} does, and run it
 * hybrid with the classical algorithm until every peer supports ML-KEM. ML-KEM encapsulates a
 * secret rather than agreeing on one, so the protocol changes shape; it is not a rename.
 */
public class PqcKeyExchangeExample {

  public byte[] buggy(PrivateKey mine, PublicKey theirs) throws Exception {
    KeyAgreement agreement = KeyAgreement.getInstance("X25519"); // :: CK-PQC-KEY-EXCHANGE
    agreement.init(mine);
    agreement.doPhase(theirs, true);
    return agreement.generateSecret();
  }

  public boolean fixed() throws Exception {
    KeyPair receiver = KeyPairGenerator.getInstance("ML-KEM").generateKeyPair();
    KEM kem = KEM.getInstance("ML-KEM");
    KEM.Encapsulated sent = kem.newEncapsulator(receiver.getPublic()).encapsulate();
    SecretKey received = kem.newDecapsulator(receiver.getPrivate()).decapsulate(sent.encapsulation());
    return received.equals(sent.key());
  }
}
