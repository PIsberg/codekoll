package examples.modern;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Example for rule {@code CK-ARENA-USE-AFTER-CLOSE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} reads from a {@code MemorySegment} after calling
 * {@code arena.close()}.
 *
 * <p><b>What happens at runtime:</b> closing an Arena frees every segment it allocated. The
 * subsequent read throws {@code IllegalStateException: Already closed} — the foreign-memory
 * equivalent of a use-after-free, caught by the runtime rather than corrupting memory, but a
 * guaranteed crash all the same.
 *
 * <p><b>How to fix it:</b> keep all segment access inside the arena's lifetime — ideally a
 * try-with-resources arena, as {@link #fixed()} does.
 */
public class ArenaUseAfterCloseExample {

  public long buggy() {
    Arena arena = Arena.ofConfined();
    MemorySegment segment = arena.allocate(8);
    segment.set(ValueLayout.JAVA_LONG, 0, 42L);
    arena.close();
    return segment.get(ValueLayout.JAVA_LONG, 0); // :: CK-ARENA-USE-AFTER-CLOSE
  }

  public long fixed() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(8);
      segment.set(ValueLayout.JAVA_LONG, 0, 42L);
      return segment.get(ValueLayout.JAVA_LONG, 0);
    }
  }
}
