package examples.apimisuse;

import java.util.Arrays;

/**
 * Example for rule {@code CK-PRIMITIVE-ARRAY-VARARGS}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(int[])} wraps an {@code int[]} with
 * {@code Arrays.asList(...)} expecting a list of the ids. Varargs spreading needs a reference
 * array — generics have no {@code int}, so {@code int[]} cannot become {@code T...}.
 *
 * <p><b>What happens at runtime:</b> the compiler infers {@code T = int[]} and returns a
 * {@code List<int[]>} holding the whole array as its single element. Nothing throws: the list
 * simply has {@code size() == 1}, {@code contains(42)} is {@code false} for every id in it, and
 * printing it yields {@code [[I@1a2b3c]}. Written with {@code Integer[]} the very same line
 * behaves exactly as intended, which is why the mistake survives review — and why the symptom
 * shows up far away, as a batch that silently processes one row.
 *
 * <p><b>How to fix it:</b> box the elements — {@code Arrays.stream(ids).boxed().toList()}, as
 * {@link #fixed(int[])} does. For streams use {@code Arrays.stream(ids)}, which gives a real
 * {@code IntStream}.
 */
public class PrimitiveArrayVarargsExample {

  public int buggy(int[] ids) {
    var idList = Arrays.asList(ids); // :: CK-PRIMITIVE-ARRAY-VARARGS
    return idList.size();
  }

  public int fixed(int[] ids) {
    var idList = Arrays.stream(ids).boxed().toList();
    return idList.size();
  }
}
