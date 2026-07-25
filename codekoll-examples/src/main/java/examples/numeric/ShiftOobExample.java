package examples.numeric;

/**
 * Example for rule {@code CK-SHIFT-OOB}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} computes a 33rd flag bit with {@code 1 << 32}
 * on an {@code int}.
 *
 * <p><b>What happens at runtime:</b> the JLS takes the shift distance modulo 32, so
 * {@code 1 << 32} is {@code 1 << 0} — the value is 1, colliding with the first flag. Flag
 * sets built this way silently share bits: setting one flag "sets" another.
 *
 * <p><b>How to fix it:</b> use a {@code long} when more than 31 bits are needed, as
 * {@link #fixed()} does.
 */
public class ShiftOobExample {

  public int buggy() {
    return 1 << 32; // :: CK-SHIFT-OOB
  }

  public long fixed() {
    return 1L << 32;
  }
}
