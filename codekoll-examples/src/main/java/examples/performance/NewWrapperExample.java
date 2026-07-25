package examples.performance;

/**
 * Example for rule {@code CK-NEW-WRAPPER}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} allocates wrappers with
 * {@code new Integer(...)} and copies a string with {@code new String(s)}.
 *
 * <p><b>What happens at runtime:</b> the boxing constructors (deprecated for removal)
 * always allocate, bypassing the −128..127 cache {@code valueOf} uses — hot paths churn
 * garbage for values the JVM would share. {@code new String(s)} duplicates an immutable
 * value: pure allocation with zero benefit.
 *
 * <p><b>How to fix it:</b> autoboxing / {@code valueOf}, and use strings directly, as
 * {@link #fixed(String)} does.
 */
public class NewWrapperExample {

  public Object[] buggy(String name) {
    Integer count = new Integer(1); // :: CK-NEW-WRAPPER
    String copy = new String(name); // :: CK-NEW-WRAPPER
    return new Object[] {count, copy};
  }

  public Object[] fixed(String name) {
    Integer count = 1;
    return new Object[] {count, name};
  }
}
