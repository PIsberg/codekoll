package examples.apimisuse;

import java.util.List;

/**
 * Example for rule {@code CK-REMOVE-INT-AMBIGUOUS}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(List, int)} wants to remove the <em>value</em>
 * {@code userId} from a {@code List<Integer>}, but passes a bare {@code int}.
 *
 * <p><b>What happens at runtime:</b> overload resolution picks {@code remove(int index)} —
 * the element at POSITION {@code userId} is deleted, not the value. The wrong user quietly
 * disappears from the list (or {@code IndexOutOfBoundsException} when the id exceeds the
 * size).
 *
 * <p><b>How to fix it:</b> box explicitly for by-value removal, as
 * {@link #fixed(List, int)} does.
 */
public class RemoveIntAmbiguousExample {

  public void buggy(List<Integer> activeUserIds, int userId) {
    activeUserIds.remove(userId); // :: CK-REMOVE-INT-AMBIGUOUS
  }

  public void fixed(List<Integer> activeUserIds, int userId) {
    activeUserIds.remove(Integer.valueOf(userId));
  }
}
