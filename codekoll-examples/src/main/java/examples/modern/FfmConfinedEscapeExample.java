package examples.modern;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Example for rule {@code CK-FFM-CONFINED-ESCAPE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(ExecutorService)} allocates a buffer from a confined
 * arena and hands the checksum work to an executor.
 *
 * <p><b>What happens at runtime:</b> a confined arena and every segment allocated from it belong
 * to the thread that created the arena. The executor's thread reads the segment, and the first
 * access throws {@code WrongThreadException}; {@code get()} rethrows it wrapped in an
 * {@code ExecutionException}. It compiles, and a test that calls the checksum code directly
 * passes.
 *
 * <p><b>How to fix it:</b> use a shared arena for memory that crosses threads, as
 * {@link #fixed(ExecutorService)} does, or create the arena inside the task.
 */
public class FfmConfinedEscapeExample {

  public long buggy(ExecutorService pool) throws InterruptedException, ExecutionException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocate(ValueLayout.JAVA_LONG, 4);
      return pool.submit(() -> buffer.getAtIndex(ValueLayout.JAVA_LONG, 0)).get(); // :: CK-FFM-CONFINED-ESCAPE
    }
  }

  public long fixed(ExecutorService pool) throws InterruptedException, ExecutionException {
    try (Arena arena = Arena.ofShared()) {
      MemorySegment buffer = arena.allocate(ValueLayout.JAVA_LONG, 4);
      return pool.submit(() -> buffer.getAtIndex(ValueLayout.JAVA_LONG, 0)).get();
    }
  }
}
