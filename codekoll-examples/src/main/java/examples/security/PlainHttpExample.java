package examples.security;

import java.net.URI;

/**
 * Example for rule {@code CK-PLAIN-HTTP}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} calls a payment API over {@code http://}.
 *
 * <p><b>What happens at runtime:</b> the request works — in cleartext. Credentials, card
 * data and session cookies are readable and modifiable by every network hop: the hotel
 * Wi-Fi, the ISP, anyone running a rogue access point. Capture-and-replay is a practical,
 * everyday attack, not a theoretical one.
 *
 * <p><b>How to fix it:</b> {@code https://}, as {@link #fixed()} does.
 */
public class PlainHttpExample {

  public URI buggy() throws Exception {
    return new URI("http://api.payments.example-corp.io/charge"); // :: CK-PLAIN-HTTP
  }

  public URI fixed() throws Exception {
    return new URI("https://api.payments.example-corp.io/charge");
  }
}
