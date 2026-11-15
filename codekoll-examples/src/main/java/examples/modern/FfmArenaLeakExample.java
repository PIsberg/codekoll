package examples.modern;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Example for rule {@code CK-FFM-ARENA-LEAK}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(byte[])} copies each message into off-heap memory from a
 * new confined arena and never closes it.
 *
 * <p><b>What happens at runtime:</b> off-heap memory from {@code Arena.ofConfined()} is released
 * only by {@code close()}. The garbage collector reclaims the arena object and leaves the memory
 * reserved, so every call leaks its buffer. The heap looks healthy while the process's resident
 * size grows until the operating system kills it.
 *
 * <p><b>How to fix it:</b> open the arena in try-with-resources, as {@link #fixed(byte[])} does.
 */
public class FfmArenaLeakExample {

  public long buggy(byte[] message) {
    Arena arena = Arena.ofConfined(); // :: CK-FFM-ARENA-LEAK
    MemorySegment copy = arena.allocate(message.length);
    MemorySegment.copy(MemorySegment.ofArray(message), 0, copy, 0, message.length);
    return copy.byteSize();
  }

  public long fixed(byte[] message) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment copy = arena.allocate(message.length);
      MemorySegment.copy(MemorySegment.ofArray(message), 0, copy, 0, message.length);
      return copy.byteSize();
    }
  }
}
