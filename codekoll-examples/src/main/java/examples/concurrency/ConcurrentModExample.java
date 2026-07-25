package examples.concurrency;

import java.util.List;

/**
 * Example for rule {@code CK-CONCURRENT-MOD}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(List)} removes elements from the same list it is
 * iterating with for-each.
 *
 * <p><b>What happens at runtime:</b> for-each uses the list's fail-fast iterator; the
 * structural modification throws {@code ConcurrentModificationException} on the next
 * iteration. The "remove the expired entries" loop crashes the moment it finds the first
 * one.
 *
 * <p><b>How to fix it:</b> {@code removeIf}, as {@link #fixed(List)} does (or
 * {@code Iterator.remove}).
 */
public class ConcurrentModExample {

  public void buggy(List<String> tokens) {
    for (String token : tokens) {
      if (token.isBlank()) {
        tokens.remove(token); // :: CK-CONCURRENT-MOD
      }
    }
  }

  public void fixed(List<String> tokens) {
    tokens.removeIf(String::isBlank);
  }
}
