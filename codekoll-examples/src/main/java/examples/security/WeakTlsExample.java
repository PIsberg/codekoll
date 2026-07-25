package examples.security;

import javax.net.ssl.SSLContext;

/**
 * Example for rule {@code CK-WEAK-TLS}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} requests an {@code SSLContext} pinned to
 * {@code "TLSv1"}.
 *
 * <p><b>What happens at runtime:</b> the connection succeeds — negotiated at a protocol
 * version with known practical attacks (BEAST; RFC 8996 formally deprecates TLS 1.0/1.1).
 * The traffic shows a reassuring padlock while being decryptable by a positioned attacker.
 *
 * <p><b>How to fix it:</b> pin {@code "TLSv1.3"} (or 1.2 minimum), as {@link #fixed()}
 * does.
 */
public class WeakTlsExample {

  public SSLContext buggy() throws Exception {
    return SSLContext.getInstance("TLSv1"); // :: CK-WEAK-TLS
  }

  public SSLContext fixed() throws Exception {
    return SSLContext.getInstance("TLSv1.3");
  }
}
