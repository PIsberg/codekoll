package io.codekoll.rules.modern;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

/** CK-FFM-CONFINED-ESCAPE, CK-FFM-ARENA-LEAK and CK-FFM-REINTERPRET-UNBOUNDED (issue #122). */
class FfmLifecycleRulesTest {

  @Test
  void confinedArenaOrSegmentOnAnotherThreadFlagged() {
    RuleTestHarness.assertFixture(new FfmConfinedEscapeRule(), "P1", """
        import java.lang.foreign.Arena;
        import java.lang.foreign.MemorySegment;
        import java.lang.foreign.ValueLayout;
        import java.util.concurrent.CompletableFuture;
        import java.util.concurrent.ExecutorService;
        class P1 {
          void thread() throws InterruptedException {
            try (Arena arena = Arena.ofConfined()) {
              MemorySegment segment = arena.allocate(8);
              Thread t = new Thread(() -> segment.set(ValueLayout.JAVA_LONG, 0, 1)); // :: CK-FFM-CONFINED-ESCAPE
              t.start();
              t.join();
            }
          }
          void executor(ExecutorService pool) {
            Arena arena = Arena.ofConfined();
            pool.submit(() -> arena.allocate(16)); // :: CK-FFM-CONFINED-ESCAPE
            arena.close();
          }
          long async() {
            try (Arena arena = Arena.ofConfined()) {
              MemorySegment segment = arena.allocate(8);
              return CompletableFuture.supplyAsync(() -> {
                return segment.get(ValueLayout.JAVA_LONG, 0); // :: CK-FFM-CONFINED-ESCAPE
              }).join();
            }
          }
          void virtual() {
            try (Arena arena = Arena.ofConfined()) {
              Thread.ofVirtual().start(() -> arena.allocate(8)); // :: CK-FFM-CONFINED-ESCAPE
            }
          }
        }
        """);
  }

  @Test
  void sharedArenasAndSameThreadUseAllowed() {
    RuleTestHarness.assertFixture(new FfmConfinedEscapeRule(), "N1", """
        import java.lang.foreign.Arena;
        import java.lang.foreign.MemorySegment;
        import java.lang.foreign.ValueLayout;
        import java.util.List;
        import java.util.concurrent.ExecutorService;
        class N1 {
          void shared(ExecutorService pool) {
            try (Arena arena = Arena.ofShared()) {
              MemorySegment segment = arena.allocate(8);
              pool.submit(() -> segment.get(ValueLayout.JAVA_LONG, 0));
            }
          }
          long sameThread(List<Long> values) {
            try (Arena arena = Arena.ofConfined()) {
              MemorySegment segment = arena.allocate(8L * values.size());
              values.forEach(v -> segment.set(ValueLayout.JAVA_LONG, 0, v));
              return segment.get(ValueLayout.JAVA_LONG, 0);
            }
          }
          void ownArena(ExecutorService pool) {
            pool.submit(() -> {
              try (Arena arena = Arena.ofConfined()) {
                return arena.allocate(8).byteSize();
              }
            });
          }
        }
        """);
  }

  @Test
  void arenaNeverClosedFlagged() {
    RuleTestHarness.assertFixture(new FfmArenaLeakRule(), "P2", """
        import java.lang.foreign.Arena;
        import java.lang.foreign.MemorySegment;
        class P2 {
          private Arena cache = Arena.ofShared(); // :: CK-FFM-ARENA-LEAK
          long local() {
            Arena arena = Arena.ofConfined(); // :: CK-FFM-ARENA-LEAK
            MemorySegment segment = arena.allocate(64);
            return segment.byteSize();
          }
          long chained() {
            return Arena.ofConfined().allocate(64).byteSize(); // :: CK-FFM-ARENA-LEAK
          }
        }
        """);
  }

  @Test
  void closedOrHandedOverArenasAllowed() {
    RuleTestHarness.assertFixture(new FfmArenaLeakRule(), "N2", """
        import java.lang.foreign.Arena;
        import java.util.ArrayList;
        import java.util.List;
        class N2 implements AutoCloseable {
          private final Arena owned = Arena.ofShared();
          private final List<Arena> pool = new ArrayList<>();
          long resource() {
            try (Arena arena = Arena.ofConfined()) {
              return arena.allocate(8).byteSize();
            }
          }
          long finallyClosed() {
            Arena arena = Arena.ofConfined();
            try {
              return arena.allocate(8).byteSize();
            } finally {
              arena.close();
            }
          }
          Arena factory() {
            return Arena.ofShared();
          }
          Arena returnedLocal() {
            Arena arena = Arena.ofConfined();
            arena.allocate(8);
            return arena;
          }
          void handedOver() {
            pool.add(Arena.ofShared());
            Arena arena = Arena.ofShared();
            pool.add(arena);
          }
          long automatic() {
            return Arena.ofAuto().allocate(8).byteSize() + Arena.global().allocate(8).byteSize();
          }
          @Override
          public void close() {
            owned.close();
          }
        }
        """);
  }

  @Test
  void unboundedReinterpretOfNativePointerFlagged() {
    RuleTestHarness.assertFixture(new FfmReinterpretUnboundedRule(), "P3", """
        import java.lang.foreign.MemorySegment;
        import java.lang.foreign.ValueLayout;
        import java.lang.invoke.MethodHandle;
        class P3 {
          MemorySegment downcall(MethodHandle strdup, MemorySegment text) throws Throwable {
            MemorySegment copy = (MemorySegment) strdup.invokeExact(text);
            return copy.reinterpret(64); // :: CK-FFM-REINTERPRET-UNBOUNDED
          }
          MemorySegment address(long raw) {
            return MemorySegment.ofAddress(raw).reinterpret(16); // :: CK-FFM-REINTERPRET-UNBOUNDED
          }
          MemorySegment pointerField(MemorySegment struct) {
            return struct.get(ValueLayout.ADDRESS, 0).reinterpret(32); // :: CK-FFM-REINTERPRET-UNBOUNDED
          }
        }
        """);
  }

  @Test
  void scopedOrArenaBackedReinterpretAllowed() {
    RuleTestHarness.assertFixture(new FfmReinterpretUnboundedRule(), "N3", """
        import java.lang.foreign.Arena;
        import java.lang.foreign.MemorySegment;
        import java.lang.invoke.MethodHandle;
        class N3 {
          MemorySegment withCleanup(MethodHandle strdup, MemorySegment text, Arena arena)
              throws Throwable {
            MemorySegment copy = (MemorySegment) strdup.invokeExact(text);
            return copy.reinterpret(64, arena, N3::free);
          }
          static void free(MemorySegment segment) {}
          long arenaBacked() {
            try (Arena arena = Arena.ofConfined()) {
              MemorySegment segment = arena.allocate(64);
              return segment.reinterpret(32).byteSize();
            }
          }
          MemorySegment unknown(MemorySegment given) {
            return given.reinterpret(8);
          }
        }
        """);
  }
}
