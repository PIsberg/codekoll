package io.codekoll.rules.deadcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class UnusedPrivateRuleTest {

  private final UnusedPrivateRule rule = new UnusedPrivateRule();

  @Test
  void metadata() { // 010 FR-005, FR-007, FR-010 (never ERROR severity)
    assertEquals("CK-UNUSED-PRIVATE", rule.id().value());
    assertEquals(RulePack.DEAD_CODE, rule.pack());
    assertEquals(Severity.INFO, rule.defaultSeverity());
    assertFalse(rule.description().isBlank() || rule.description().equals("TODO"));
    assertFalse(rule.explanation().isBlank() || rule.explanation().equals("TODO"));
    assertFalse(rule.fix().isBlank() || rule.fix().equals("TODO"));
  }

  @Test
  void flagsUnusedPrivateMethodAndNamesIt() { // 010 FR-003 FR-005 US2-scenario-1
    String source = """
        class P1 {
          private void helper() {}
        }
        """;
    RuleTestHarness.assertFixture(rule, "P1", """
        class P1 {
          private void helper() {} // :: CK-UNUSED-PRIVATE
        }
        """);
    var findings = RuleTestHarness.run(rule, "P1", source).findings();
    assertEquals(1, findings.size());
    assertTrue(findings.get(0).message().contains("helper"), findings.get(0).message());
  }

  @Test
  void flagsNeverReferencedField() { // 010 FR-003
    RuleTestHarness.assertFixture(rule, "P2", """
        class P2 {
          private int count; // :: CK-UNUSED-PRIVATE
        }
        """);
  }

  @Test
  void flagsWrittenButNeverReadFieldWithNeverReadWording() { // 010 FR-003 FR-005 US2-scenario-2
    String source = """
        class P3 {
          private int total; // :: CK-UNUSED-PRIVATE
          void set() { this.total = 5; }
        }
        """;
    RuleTestHarness.assertFixture(rule, "P3", source);
    var findings = RuleTestHarness.run(rule, "P3", source).findings();
    assertEquals(1, findings.size());
    String msg = findings.get(0).message();
    assertTrue(msg.contains("total") && msg.toLowerCase().contains("never read"), msg);
  }

  @Test
  void flagsUnusedPrivateMemberOfNestedClass() { // 010 FR-003
    RuleTestHarness.assertFixture(rule, "P4", """
        class P4 {
          static class In {
            private void dead() {} // :: CK-UNUSED-PRIVATE
          }
          int use() { return new In().hashCode(); }
        }
        """);
  }

  @Test
  void flagsSerializationNameWithWrongParameterTypes() { // 010 FR-003 FR-004
    RuleTestHarness.assertFixture(rule, "P5", """
        class P5 {
          private void writeObject(String s) {} // :: CK-UNUSED-PRIVATE
        }
        """);
  }

  @Test
  void flagsFieldOfClassWithOnlyJavaLangAnnotation() { // 010 FR-004
    RuleTestHarness.assertFixture(rule, "P6", """
        @Deprecated
        class P6 {
          private int dead; // :: CK-UNUSED-PRIVATE
        }
        """);
  }

  @Test
  void usedMembersAreSilent() { // 010 FR-003 US2
    RuleTestHarness.assertFixture(rule, "N1", """
        class N1 {
          private int f = 1;
          private int g() { return f; }
          int pub() { return g(); }
        }
        """);
  }

  @Test
  void silentWhenUsedOnlyFromNestedClass() { // 010 FR-003 FR-009
    RuleTestHarness.assertFixture(rule, "N2", """
        class N2 {
          private int f = 1;
          private int g() { return 2; }
          class In { int h() { return f + g(); } }
        }
        """);
  }

  @Test
  void assignmentThroughFieldKeepsReceiverUsed() { // 010 FR-003
    RuleTestHarness.assertFixture(rule, "N3", """
        class N3 {
          static class Holder { int f; int x; }
          private Holder holder = new Holder();
          private Holder cfg = new Holder();
          void set(int v) {
            holder.f = v;
            this.cfg.x = v;
          }
        }
        """);
  }

  @Test
  void exemption1MethodReference() { // 010 FR-004 FR-008 US2-scenario-4
    RuleTestHarness.assertFixture(rule, "N4", """
        class N4 {
          private void helper() {}
          Runnable r() { return this::helper; }
        }
        """);
  }

  @Test
  void exemption1LambdaBody() { // 010 FR-004 FR-008 US2-scenario-4
    RuleTestHarness.assertFixture(rule, "N5", """
        class N5 {
          private void helper() {}
          private int field = 1;
          Runnable r() { return () -> { helper(); System.out.println(field); }; }
        }
        """);
  }

  @Test
  void exemption2MemberAnnotation() { // 010 FR-004 FR-008 US2-scenario-3
    RuleTestHarness.assertFixture(rule, "N6", """
        class N6 {
          @interface Inject {}
          @Inject private Object dep;
          @Inject private void init() {}
        }
        """);
  }

  @Test
  void exemption3TypeAnnotationOnFieldsClass() { // 010 FR-004 FR-008 US2-scenario-3
    RuleTestHarness.assertFixture(rule, "N7", """
        @interface Entity {}
        @Entity
        class N7 {
          private int id;
          class Inner { private int other; }
        }
        """);
  }

  @Test
  void exemption4SerializationMembers() { // 010 FR-004 FR-008 US2-scenario-3
    RuleTestHarness.assertFixture(rule, "N8", """
        import java.io.IOException;
        import java.io.ObjectInputStream;
        import java.io.ObjectOutputStream;
        import java.io.ObjectStreamField;
        import java.io.Serializable;
        class N8 implements Serializable {
          private static final long serialVersionUID = 1L;
          private static final ObjectStreamField[] serialPersistentFields = {};
          private void writeObject(ObjectOutputStream o) throws IOException {}
          private void readObject(ObjectInputStream i) throws IOException, ClassNotFoundException {}
          private void readObjectNoData() {}
          private Object writeReplace() { return this; }
          private Object readResolve() { return this; }
        }
        """);
  }

  @Test
  void exemption5NameAppearsAsStringLiteral() { // 010 FR-004 FR-008
    RuleTestHarness.assertFixture(rule, "N9", """
        class N9 {
          private int hidden;
          private void viaReflection() {}
          String[] names() { return new String[] {"hidden", "viaReflection"}; }
        }
        """);
  }

  @Test
  void exemption6SameNameAsAnnotatedMethod() { // 010 FR-004 FR-008
    RuleTestHarness.assertFixture(rule, "N10", """
        class N10 {
          @interface Test {}
          @Test void check() {}
          private static Object check(int unused) { return null; }
        }
        """);
  }

  @Test
  void exemption7RecordComponent() { // 010 FR-004 FR-008
    RuleTestHarness.assertFixture(rule, "N11", """
        record N11(int a, String b) {}
        """);
  }

  @Test
  void privateConstructorIsSilent() { // 010 FR-003
    RuleTestHarness.assertFixture(rule, "N12", """
        class N12 {
          private N12() {}
        }
        """);
  }

  @Test
  void nonPrivateMembersAreSilent() { // 010 FR-009
    RuleTestHarness.assertFixture(rule, "N13", """
        public class N13 {
          int pkg;
          protected int prot;
          public int pub;
          void pkgM() {}
          protected void protM() {}
          public void pubM() {}
        }
        """);
  }
}
