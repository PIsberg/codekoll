package examples.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Example for rule {@code CK-CRYPTO-WEAK}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} requests the MD5 digest algorithm.
 *
 * <p><b>What happens at runtime:</b> it runs fine — and produces hashes that are
 * cryptographically worthless: MD5 collisions can be generated in seconds, so an attacker can
 * forge data that passes the integrity check.
 *
 * <p><b>How to fix it:</b> use SHA-256 or stronger, as {@link #fixed()} does.
 */
public class CryptoWeakExample {

  public byte[] buggy(byte[] data) throws NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("MD5"); // :: CK-CRYPTO-WEAK
    return digest.digest(data);
  }

  public byte[] fixed(byte[] data) throws NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return digest.digest(data);
  }
}
