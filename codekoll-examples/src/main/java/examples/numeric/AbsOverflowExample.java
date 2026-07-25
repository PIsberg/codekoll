package examples.numeric;

/**
 * Example for rule {@code CK-ABS-OVERFLOW}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String, int)} picks a shard with
 * {@code Math.abs(key.hashCode()) % shards}.
 *
 * <p><b>What happens at runtime:</b> {@code Integer.MIN_VALUE} has no positive counterpart,
 * so {@code Math.abs(Integer.MIN_VALUE)} is still {@code Integer.MIN_VALUE} — negative.
 * Roughly one hash in four billion produces a negative shard index and an
 * {@code ArrayIndexOutOfBoundsException} that is essentially unreproducible in tests.
 *
 * <p><b>How to fix it:</b> {@code Math.floorMod}, which is always non-negative, as
 * {@link #fixed(String, int)} does.
 */
public class AbsOverflowExample {

  public int buggy(String key, int shards) {
    return Math.abs(key.hashCode()) % shards; // :: CK-ABS-OVERFLOW
  }

  public int fixed(String key, int shards) {
    return Math.floorMod(key.hashCode(), shards);
  }
}
