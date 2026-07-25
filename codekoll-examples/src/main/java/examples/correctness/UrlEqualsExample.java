package examples.correctness;

import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Example for rule {@code CK-URL-EQUALS}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(URL, URL)} compares two {@code java.net.URL}
 * objects with {@code equals}.
 *
 * <p><b>What happens at runtime:</b> URL.equals resolves the host over the network — a
 * blocking DNS lookup inside a "pure" comparison. Two different hostnames on the same
 * server compare equal, comparisons hang when DNS is slow, and a URL Set does network I/O
 * on every add.
 *
 * <p><b>How to fix it:</b> use {@code java.net.URI}, which compares by string components
 * with no network access, as {@link #fixed(URI, URI)} does.
 */
public class UrlEqualsExample {

  public boolean buggy(URL a, URL b) {
    Set<URL> seen = new HashSet<>();
    seen.add(a);
    return a.equals(b) || seen.contains(b); // :: CK-URL-EQUALS
  }

  public boolean fixed(URI a, URI b) {
    Set<URI> seen = new HashSet<>();
    seen.add(a);
    return a.equals(b) || seen.contains(b);
  }
}
