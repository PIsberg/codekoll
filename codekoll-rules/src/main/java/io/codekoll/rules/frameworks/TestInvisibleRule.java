package io.codekoll.rules.frameworks;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;

/**
 * CK-TEST-INVISIBLE: a {@code @Test} method that is private, static, or returns non-void —
 * JUnit silently skips it; the suite reports green by never running the test.
 */
// The *Rule suffix trips PMD's is-this-a-test-class heuristic; it is an analyzer rule.
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class TestInvisibleRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-TEST-INVISIBLE");

  private static final Set<String> TEST_ANNOTATIONS =
      Set.of("Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.FRAMEWORKS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "@Test method that is private, static, or returns a value";
  }

  @Override
  public String explanation() {
    return "JUnit discovers test methods reflectively and requires them to be non-private, "
        + "non-static and void (except @TestFactory). A private @Test is not discovered — "
        + "the suite passes with the test SILENTLY never running. The assertion that would "
        + "have caught the regression sits there, green, executing zero times.";
  }

  @Override
  public String fix() {
    return "Make the test method package-private (or public), non-static, and void.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethod(MethodTree node, RuleContext ctx) {
        String annotation = testAnnotation(node);
        if (annotation != null && !"TestFactory".equals(annotation)) {
          Set<Modifier> flags = node.getModifiers().getFlags();
          String blocker = flags.contains(Modifier.PRIVATE) ? "private"
              : flags.contains(Modifier.STATIC) ? "static"
              : returnsValue(node) ? "value-returning"
              : null;
          if (blocker != null) {
            ctx.report(node, "A " + blocker + " @" + annotation + " method is not "
                + "discovered by JUnit — the test silently never runs. Make it "
                + "package-private, non-static, and void.");
          }
        }
        return super.visitMethod(node, ctx);
      }

      private boolean returnsValue(MethodTree method) {
        Tree returnType = method.getReturnType();
        return returnType != null
            && !(returnType instanceof com.sun.source.tree.PrimitiveTypeTree primitive
                && primitive.getPrimitiveTypeKind() == TypeKind.VOID);
      }

      private String testAnnotation(MethodTree method) {
        for (AnnotationTree annotation : method.getModifiers().getAnnotations()) {
          String text = annotation.getAnnotationType().toString();
          String name = text.substring(text.lastIndexOf('.') + 1);
          if (TEST_ANNOTATIONS.contains(name)) {
            return name;
          }
        }
        return null;
      }
    };
  }
}
