package io.codekoll.rules.modern;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ModernRulesTest {

  @Test
  void sealedSwitchDefaultFlagged() {
    RuleTestHarness.assertFixture(new SealedSwitchDefaultRule(), "P1", """
        class P1 {
          sealed interface Shape permits Circle, Square {}
          record Circle(double r) implements Shape {}
          record Square(double side) implements Shape {}
          double area(Shape s) {
            return switch (s) { // :: CK-SEALED-SWITCH-DEFAULT
              case Circle c -> Math.PI * c.r() * c.r();
              default -> 0.0;
            };
          }
        }
        """);
  }

  @Test
  void exhaustiveSealedSwitchAndNonSealedDefaultAllowed() {
    RuleTestHarness.assertFixture(new SealedSwitchDefaultRule(), "N1", """
        class N1 {
          sealed interface Shape permits Circle, Square {}
          record Circle(double r) implements Shape {}
          record Square(double side) implements Shape {}
          double area(Shape s) {
            return switch (s) {
              case Circle c -> Math.PI * c.r() * c.r();
              case Square q -> q.side() * q.side();
            };
          }
          String describe(int code) {
            return switch (code) {
              case 1 -> "one";
              default -> "many";
            };
          }
        }
        """);
  }

  @Test
  void recordArrayComponentFlagged() {
    RuleTestHarness.assertFixture(new RecordArrayComponentRule(), "P2", """
        class P2 {
          record Checksum(String algorithm, byte[] digest) {} // :: CK-RECORD-ARRAY-COMPONENT
        }
        """);
  }

  @Test
  void listComponentAndExplicitEqualsAllowed() {
    RuleTestHarness.assertFixture(new RecordArrayComponentRule(), "N2", """
        import java.util.Arrays;
        import java.util.List;
        class N2 {
          record Checksum(String algorithm, List<Byte> digest) {}
          record Raw(byte[] digest) {
            @Override
            public boolean equals(Object o) {
              return o instanceof Raw other && Arrays.equals(digest, other.digest);
            }
            @Override
            public int hashCode() {
              return Arrays.hashCode(digest);
            }
          }
        }
        """);
  }

  @Test
  void streamReuseFlagged() {
    RuleTestHarness.assertFixture(new StreamReuseRule(), "P3", """
        import java.util.stream.Stream;
        class P3 {
          long m(Stream<String> names) {
            long count = names.count();
            long distinct = names.count(); // :: CK-STREAM-REUSE
            return count + distinct;
          }
        }
        """);
  }

  @Test
  void freshStreamsAndCollectionsAllowed() {
    RuleTestHarness.assertFixture(new StreamReuseRule(), "N3", """
        import java.util.List;
        import java.util.stream.Stream;
        class N3 {
          long m(List<String> source) {
            long count = source.stream().count();
            long distinct = source.stream().distinct().count();
            Stream<String> once = source.stream();
            return count + distinct + once.count();
          }
        }
        """);
  }

  @Test
  void chronoUnsupportedFlagged() {
    RuleTestHarness.assertFixture(new ChronoUnsupportedRule(), "P4", """
        import java.time.Instant;
        import java.time.LocalDate;
        import java.time.temporal.ChronoUnit;
        class P4 {
          Object[] m(Instant now, LocalDate today) {
            Instant later = now.plus(1, ChronoUnit.MONTHS); // :: CK-CHRONO-UNSUPPORTED
            LocalDate soon = today.plus(2, ChronoUnit.HOURS); // :: CK-CHRONO-UNSUPPORTED
            return new Object[] {later, soon};
          }
        }
        """);
  }

  @Test
  void supportedUnitsAndConversionsAllowed() {
    RuleTestHarness.assertFixture(new ChronoUnsupportedRule(), "N4", """
        import java.time.Instant;
        import java.time.LocalDate;
        import java.time.ZoneOffset;
        import java.time.temporal.ChronoUnit;
        class N4 {
          Object[] m(Instant now, LocalDate today) {
            Instant later = now.plus(3, ChronoUnit.HOURS);
            LocalDate soon = today.plus(1, ChronoUnit.MONTHS);
            Instant viaZone = now.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
            return new Object[] {later, soon, viaZone};
          }
        }
        """);
  }
}
