package io.codekoll.workspace;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Module boundaries for {@code io.codekoll.workspace}, as tests.
 *
 * <p>JPMS already enforces most of this at compile time; these tests state it where JPMS cannot
 * (the classpath build the shaded jar uses) and where the constraint is about intent rather than
 * readability. The point of the boundary: discovery decides <em>what</em> to analyze and must
 * never need the analyzer to do it, so that a workspace can be printed, tested and reasoned about
 * without a {@code JavacTask} anywhere near it.
 */
class WorkspaceArchitectureTest {

  private static final JavaClasses CLASSES = new ClassFileImporter()
      .withImportOption(new ImportOption.DoNotIncludeTests())
      .importPackages("io.codekoll.workspace..");

  @Test
  void workspaceDoesNotDependOnTheEngine() {
    noClasses().that().resideInAPackage("io.codekoll.workspace..")
        .should().dependOnClassesThat().resideInAPackage("io.codekoll.engine..")
        .because("discovery must be usable without the analyzer; the CLI wires the two together")
        .check(CLASSES);
  }

  @Test
  void workspaceDoesNotDependOnReporters() {
    noClasses().that().resideInAPackage("io.codekoll.workspace..")
        .should().dependOnClassesThat().resideInAPackage("io.codekoll.report..")
        .because("discovery produces a model; formatting it is the reporters' job")
        .check(CLASSES);
  }

  @Test
  void workspaceDoesNotDependOnRules() {
    noClasses().that().resideInAPackage("io.codekoll.workspace..")
        .should().dependOnClassesThat().resideInAPackage("io.codekoll.rules..")
        .check(CLASSES);
  }

  @Test
  void workspaceDoesNotTouchTheCompiler() {
    noClasses().that().resideInAPackage("io.codekoll.workspace..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.sun.source..", "com.sun.tools..", "javax.lang.model..")
        .because("jdk.compiler is the engine's dependency, never discovery's")
        .check(CLASSES);
  }

  @Test
  void noJavacInternals() {
    noClasses().should().dependOnClassesThat().resideInAPackage("com.sun.tools.javac..")
        .check(CLASSES);
  }

  @Test
  void discoveryDoesNotWriteToStandardOut() {
    noClasses().that().resideInAPackage("io.codekoll.workspace..")
        .should().accessField(System.class, "out")
        .orShould().accessField(System.class, "err")
        .because("only the CLI writes to the console; discovery reports through diagnostics()")
        .check(CLASSES);
  }

  @Test
  void discoveryNeverExitsTheJvm() {
    noClasses().that().resideInAPackage("io.codekoll.workspace..")
        .should().callMethod(System.class, "exit", int.class)
        .because("only the CLI decides the exit code")
        .check(CLASSES);
  }

  @Test
  void discoveryDoesNotStartSubprocesses() {
    noClasses().that().resideInAPackage("io.codekoll.workspace..")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName("java.lang.ProcessBuilder")
        .because("CLI-SPEC §4.3: invoking the target's build tool is gated and belongs to the "
            + "build-mode resolver, which does not exist yet — discover mode is hermetic")
        .check(CLASSES);
  }
}
