package io.codekoll.rules.performance;

import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-BOXED-ACCUMULATOR: {@code +=}/{@code *=}… inside a loop whose target is a boxed
 * Integer/Long/Double — every iteration unboxes, computes, and re-boxes (one allocation per
 * iteration).
 */
public final class BoxedAccumulatorRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-BOXED-ACCUMULATOR");

  private static final Set<String> BOXED_NUMERIC =
      Set.of("java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.lang.Float");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.PERFORMANCE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Boxed numeric accumulator updated inside a loop";
  }

  @Override
  public String explanation() {
    return "total += x on a boxed Long is unbox-add-REBOX: a fresh Long allocation on every "
        + "single iteration (the cache only covers -128..127). A million-element loop "
        + "allocates a million objects to compute one number — GC pressure a primitive "
        + "would avoid entirely.";
  }

  @Override
  public String fix() {
    return "Declare the accumulator as a primitive (long/double); box once at the end if a "
        + "wrapper is needed.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitCompoundAssignment(CompoundAssignmentTree node, RuleContext ctx) {
        if (NullFacts.unwrap(node.getVariable()) instanceof IdentifierTree
            && insideLoop()) {
          TypeMirror type =
              ctx.typeOf(new TreePath(getCurrentPath(), node.getVariable()));
          String name = ctx.qualifiedNameOf(type);
          if (BOXED_NUMERIC.contains(name)) {
            ctx.report(node, "Accumulating into a boxed " + name.replaceFirst(".*\\.", "")
                + " re-boxes every iteration (one allocation each). "
                + "Use a primitive accumulator.");
          }
        }
        return super.visitCompoundAssignment(node, ctx);
      }

      private boolean insideLoop() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          Tree leaf = p.getLeaf();
          if (leaf instanceof LambdaExpressionTree
              || leaf instanceof com.sun.source.tree.MethodTree) {
            return false;
          }
          if (leaf instanceof ForLoopTree || leaf instanceof EnhancedForLoopTree
              || leaf instanceof WhileLoopTree || leaf instanceof DoWhileLoopTree) {
            return true;
          }
        }
        return false;
      }
    };
  }
}
