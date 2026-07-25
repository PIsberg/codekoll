package examples.security;

import java.security.SecureRandom;

/**
 * Example for rule {@code CK-INSECURE-RANDOM}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} generates a session token from
 * {@code Math.random()}.
 *
 * <p><b>What happens at runtime:</b> {@code java.util.Random} (behind Math.random) is a
 * deterministic 48-bit generator — its full state can be recovered from a couple of
 * observed outputs, after which every past and future token is predictable. An attacker
 * who sees two tokens can forge everyone else's session.
 *
 * <p><b>How to fix it:</b> generate security material from {@code SecureRandom}, as
 * {@link #fixed()} does.
 */
public class InsecureRandomExample {

  public String buggy() {
    long sessionToken = (long) (Math.random() * Long.MAX_VALUE); // :: CK-INSECURE-RANDOM
    return Long.toHexString(sessionToken);
  }

  public String fixed() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return java.util.HexFormat.of().formatHex(bytes);
  }
}
