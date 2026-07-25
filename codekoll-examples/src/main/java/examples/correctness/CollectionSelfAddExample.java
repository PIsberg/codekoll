package examples.correctness;

import java.util.ArrayList;
import java.util.List;

/**
 * Example for rule {@code CK-COLLECTION-SELF-ADD}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} adds a list to itself.
 *
 * <p><b>What happens at runtime:</b> the list now contains itself, so {@code hashCode()}
 * and {@code toString()} recurse forever — the first call to either (often deep inside a
 * logging framework or a HashSet insertion) throws {@code StackOverflowError} far from
 * this line.
 *
 * <p><b>How to fix it:</b> add the intended element, as {@link #fixed()} does.
 */
public class CollectionSelfAddExample {

  public List<Object> buggy() {
    List<Object> items = new ArrayList<>();
    items.add(items); // :: CK-COLLECTION-SELF-ADD
    return items;
  }

  public List<Object> fixed() {
    List<Object> items = new ArrayList<>();
    items.add("first item");
    return items;
  }
}
