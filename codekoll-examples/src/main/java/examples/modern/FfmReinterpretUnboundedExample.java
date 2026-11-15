package examples.modern;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.function.Consumer;

/**
 * Example for rule {@code CK-FFM-REINTERPRET-UNBOUNDED}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(MemorySegment)} calls the C function {@code strdup},
 * which mallocs a copy that the caller must free, and makes the returned pointer readable with
 * {@code reinterpret(size)}.
 *
 * <p><b>What happens at runtime:</b> the pointer comes back as a zero-length segment in the
 * global scope, and {@code reinterpret(size)} keeps that scope. No arena owns the copy, so it is
 * never freed: one leak per call, invisible to every heap tool. Had the code freed it by hand,
 * the segment would still be readable afterwards, a use-after-free the runtime cannot detect.
 *
 * <p><b>How to fix it:</b> attach the memory to an arena with a cleanup that calls
 * {@code free}, as {@link #fixed(MemorySegment, Arena)} does: freed when the arena closes,
 * unusable afterwards.
 */
public class FfmReinterpretUnboundedExample {

  private final MethodHandle strdup;
  private final Consumer<MemorySegment> free;

  /** {@code free} is the binding's wrapper around the C function of the same name. */
  public FfmReinterpretUnboundedExample(MethodHandle strdup, Consumer<MemorySegment> free) {
    this.strdup = strdup;
    this.free = free;
  }

  public MemorySegment buggy(MemorySegment text) throws Throwable {
    MemorySegment copy = (MemorySegment) strdup.invokeExact(text);
    return copy.reinterpret(text.byteSize()); // :: CK-FFM-REINTERPRET-UNBOUNDED
  }

  public MemorySegment fixed(MemorySegment text, Arena arena) throws Throwable {
    MemorySegment copy = (MemorySegment) strdup.invokeExact(text);
    return copy.reinterpret(text.byteSize(), arena, free);
  }
}
