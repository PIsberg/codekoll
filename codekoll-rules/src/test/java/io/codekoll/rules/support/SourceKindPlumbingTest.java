package io.codekoll.rules.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.Finding;
import io.codekoll.api.Rule;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.engine.CompilationDriver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The test-source signal must come from the build's source sets, not from the path: a build can put
 * tests anywhere. These roots are deliberately named so that the directory convention would not find
 * them.
 */
class SourceKindPlumbingTest {

  @TempDir
  Path dir;

  /** Reports the source kind of every unit it sees, so the test can assert what reached the rule. */
  private static final class KindProbe extends AbstractRule {
    @Override
    public RuleId id() {
      return new RuleId("CK-KIND-PROBE");
    }

    @Override
    public RulePack pack() {
      return RulePack.CORRECTNESS;
    }

    @Override
    public Severity defaultSeverity() {
      return Severity.INFO;
    }

    @Override
    public String description() {
      return "probe";
    }

    @Override
    public String explanation() {
      return "probe";
    }

    @Override
    public String fix() {
      return "probe";
    }

    @Override
    protected TreePathScanner<Void, RuleContext> scanner() {
      return new TreePathScanner<>() {
        @Override
        public Void visitCompilationUnit(CompilationUnitTree node, RuleContext ctx) {
          ctx.report(node, ctx.sourceKind() + "/" + SourceKinds.isTestSource(ctx));
          return null;
        }
      };
    }
  }

  private List<String> kinds(boolean tellDriverAboutTests) throws IOException {
    Path main = Files.createDirectories(dir.resolve("production/java"));
    Path tests = Files.createDirectories(dir.resolve("checks/java"));
    Files.writeString(main.resolve("Main1.java"), "class Main1 {}", StandardCharsets.UTF_8);
    Files.writeString(tests.resolve("Check1.java"), "class Check1 {}", StandardCharsets.UTF_8);
    CompilationDriver driver = new CompilationDriver(25, "");
    List<Path> roots = List.of(main, tests);
    List<Rule> rules = List.of(new KindProbe());
    return (tellDriverAboutTests
        ? driver.analyzePaths(roots, List.of(tests), rules)
        : driver.analyzePaths(roots, rules))
        .findings().stream().map(Finding::message).sorted().toList();
  }

  @Test
  void discoveredTestRootsReachTheRule() throws IOException {
    assertEquals(List.of("MAIN/false", "TEST/true"), kinds(true));
  }

  @Test
  void withoutTestRootsTheKindIsUnknownAndThePathConventionDecides() throws IOException {
    assertEquals(List.of("UNKNOWN/false", "UNKNOWN/false"), kinds(false));
  }
}
