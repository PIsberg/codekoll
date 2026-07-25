package io.codekoll.rules.modern;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ModernBatch3RulesTest {

  @Test
  void recordMutableComponentFlagged() {
    RuleTestHarness.assertFixture(new RecordMutableComponentRule(), "P1", """
        import java.util.List;
        class P1 {
          record Order(String id, List<String> items) {} // :: CK-RECORD-MUTABLE-COMPONENT
        }
        """);
  }

  @Test
  void defensiveCopyAndImmutableComponentsAllowed() {
    RuleTestHarness.assertFixture(new RecordMutableComponentRule(), "N1", """
        import java.util.List;
        class N1 {
          record Copied(String id, List<String> items) {
            Copied {
              items = List.copyOf(items);
            }
          }
          record Plain(String id, int count) {}
        }
        """);
  }

  @Test
  void structuredGetBeforeJoinFlagged() {
    // Local stub mirrors java.util.concurrent.StructuredTaskScope (a preview API), so the
    // fixture compiles without --enable-preview while still exercising the name match.
    RuleTestHarness.assertFixture(new StructuredGetBeforeJoinRule(), "P2", """
        class P2 {
          static class StructuredTaskScope {
            void join() {}
            static class Subtask<T> {
              T get() {
                return null;
              }
            }
          }
          String m(StructuredTaskScope scope, StructuredTaskScope.Subtask<String> task) {
            String early = task.get(); // :: CK-STRUCTURED-GET-BEFORE-JOIN
            scope.join();
            return early;
          }
        }
        """);
  }

  @Test
  void getAfterJoinAllowed() {
    RuleTestHarness.assertFixture(new StructuredGetBeforeJoinRule(), "N2", """
        class N2 {
          static class StructuredTaskScope {
            void join() {}
            static class Subtask<T> {
              T get() {
                return null;
              }
            }
          }
          String m(StructuredTaskScope scope, StructuredTaskScope.Subtask<String> task) {
            scope.join();
            return task.get();
          }
        }
        """);
  }

  @Test
  void arenaUseAfterCloseFlagged() {
    RuleTestHarness.assertFixture(new ArenaUseAfterCloseRule(), "P3", """
        import java.lang.foreign.Arena;
        import java.lang.foreign.MemorySegment;
        import java.lang.foreign.ValueLayout;
        class P3 {
          long m() {
            Arena arena = Arena.ofConfined();
            MemorySegment segment = arena.allocate(8);
            arena.close();
            return segment.get(ValueLayout.JAVA_LONG, 0); // :: CK-ARENA-USE-AFTER-CLOSE
          }
        }
        """);
  }

  @Test
  void useBeforeCloseAllowed() {
    RuleTestHarness.assertFixture(new ArenaUseAfterCloseRule(), "N3", """
        import java.lang.foreign.Arena;
        import java.lang.foreign.MemorySegment;
        import java.lang.foreign.ValueLayout;
        class N3 {
          long m() {
            try (Arena arena = Arena.ofConfined()) {
              MemorySegment segment = arena.allocate(8);
              return segment.get(ValueLayout.JAVA_LONG, 0);
            }
          }
        }
        """);
  }
}
