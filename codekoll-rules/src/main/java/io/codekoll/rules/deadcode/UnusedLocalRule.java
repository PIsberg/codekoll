package io.codekoll.rules.deadcode;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

/** CK-UNUSED-LOCAL: a local variable that is never read. */
public final class UnusedLocalRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-UNUSED-LOCAL");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.DEAD_CODE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Local variable that is never used or never read";
  }

  @Override
  public String explanation() {
    return "A local that is only declared or only assigned does no work. Enhanced-for "
        + "variables, the unnamed variable, annotated locals and names starting with ignored "
        + "or unused are exempt.";
  }

  @Override
  public String fix() {
    return "Remove the variable, or use its value.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      private Set<Element> reads = Set.of();

      @Override
      public Void visitCompilationUnit(CompilationUnitTree node, RuleContext ctx) {
        reads = Reads.in(node, ctx.trees());
        return super.visitCompilationUnit(node, ctx);
      }

      @Override
      public Void visitVariable(VariableTree node, RuleContext ctx) {
        Element e = ctx.trees().getElement(getCurrentPath());
        if (e != null && e.getKind() == ElementKind.LOCAL_VARIABLE && !isExempt(node)
            && !reads.contains(e)) {
          String name = node.getName().toString();
          ctx.report(node, node.getInitializer() == null
              ? "Local variable '" + name + "' is never used"
              : "Local variable '" + name + "' is assigned but never read");
        }
        return super.visitVariable(node, ctx);
      }

      private boolean isExempt(VariableTree node) {
        String name = node.getName().toString();
        return name.isEmpty() || name.equals("_") || name.startsWith("ignored") || name.startsWith("unused")
            || !node.getModifiers().getAnnotations().isEmpty()
            || getCurrentPath().getParentPath().getLeaf() instanceof EnhancedForLoopTree;
      }
    };
  }
}
