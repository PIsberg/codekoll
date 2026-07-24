package examples.performance;

import java.util.List;

/**
 * Example for rule {@code CK-STR-CONCAT-LOOP}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(List)} appends to a String variable inside a loop.
 * Strings are immutable, so every {@code +=} allocates a brand-new string and copies
 * everything accumulated so far.
 *
 * <p><b>What happens at runtime:</b> total work grows quadratically with the input —
 * O(n²) characters copied. Unnoticeable at 100 records; at 100 000 records it burns seconds
 * of CPU and floods the garbage collector, typically discovered as a production hot spot.
 *
 * <p><b>How to fix it:</b> accumulate in a {@code StringBuilder} and call {@code toString()}
 * once after the loop, as {@link #fixed(List)} does.
 */
public class StrConcatLoopExample {

  public String buggy(List<String> records) {
    String report = "";
    for (String record : records) {
      report += record + "\n"; // :: CK-STR-CONCAT-LOOP
    }
    return report;
  }

  public String fixed(List<String> records) {
    StringBuilder report = new StringBuilder();
    for (String record : records) {
      report.append(record).append('\n');
    }
    return report.toString();
  }
}
