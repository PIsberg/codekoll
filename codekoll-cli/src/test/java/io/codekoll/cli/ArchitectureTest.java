package io.codekoll.cli;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.codekoll.api.Rule;
import io.codekoll.engine.RuleRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Executable half of ARCHITECTURE.md: the module boundaries and rule contract as tests.
 * JPMS enforces most of this at compile time; these tests catch what it cannot express and
 * guard the classpath (non-JPMS) build the shaded jar uses.
 */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter().importPackages("io.codekoll..");

  @Test
  void rulesDoNotDependOnReporters() {
    noClasses().that().resideInAPackage("io.codekoll.rules..")
        .should().dependOnClassesThat().resideInAPackage("io.codekoll.report..")
        .check(CLASSES);
  }

  @Test
  void noJavacInternals() {
    noClasses().should().dependOnClassesThat()
        .resideInAPackage("com.sun.tools.javac..")
        .check(CLASSES);
  }

  @Test
  void onlyEngineTouchesJavacTask() {
    noClasses().that().resideOutsideOfPackage("io.codekoll.engine..")
        .should().dependOnClassesThat().haveFullyQualifiedName("com.sun.source.util.JavacTask")
        .check(CLASSES);
  }

  @Test
  void everyRuleHasCompleteMetadata() {
    List<Rule> rules = RuleRegistry.loadAll();
    assertFalse(rules.isEmpty(), "ServiceLoader found no rules — registration broken");
    for (Rule rule : rules) {
      String id = rule.id().value();
      assertNotNull(rule.pack(), id + " has no pack");
      assertFalse(rule.description().isBlank(), id + " has blank description()");
      assertFalse(rule.explanation().isBlank(), id + " has blank explanation()");
      assertFalse(rule.fix().isBlank(), id + " has blank fix()");
      assertTrue(rule.explanation().length() > 40,
          id + " explanation() too thin to teach anything");
    }
  }
}
