package examples.modern;

import java.util.List;

/**
 * Example for rule {@code CK-RECORD-MUTABLE-COMPONENT}.
 *
 * <p><b>What is wrong:</b> the {@code buggy} record stores a {@code List} component directly.
 *
 * <p><b>What happens at runtime:</b> records look immutable, but the component holds the
 * caller's reference. Whoever passed the list — or anyone the record is later handed to —
 * can mutate it afterwards, silently changing the "immutable" record's contents behind the
 * back of everything holding it (including anything that cached its hashCode).
 *
 * <p><b>How to fix it:</b> defensively copy in a compact constructor, as the {@code fixed}
 * record does.
 */
public class RecordMutableComponentExample {

  record buggy(String id, List<String> items) {} // :: CK-RECORD-MUTABLE-COMPONENT

  record fixed(String id, List<String> items) {
    fixed {
      items = List.copyOf(items);
    }
  }
}
