package io.codekoll.rules.resources;

import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;

/**
 * CK-THROW-IN-FINALLY: {@code throw}/{@code return} inside a {@code finally} block silently
 * discards any in-flight exception from the try body.
 */
public final class ThrowInFinallyRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-THROW-IN-FINALLY");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.RESOURCES;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "throw or return inside finally discards the in-flight exception";
  }

  @Override
  public String explanation() {
    return "A finally block runs while an exception may be propagating. A return or throw "
        + "inside it REPLACES that exception: the original error — the one that explains "
        + "what actually went wrong — vanishes without a trace, leaving only the finally's "
        + "own (often unrelated) outcome.";
  }

  @Override
  public String fix() {
    return "Keep finally to cleanup only. Rethrow/return in the try or catch blocks; if the "
        + "cleanup itself can fail, addSuppressed the secondary exception.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitThrow(ThrowTree node, RuleContext ctx) {
        if (isDirectlyInFinally()) {
          ctx.report(node, "throw in finally replaces any in-flight exception — the "
              + "original failure is lost. Move it to try/catch.");
        }
        return super.visitThrow(node, ctx);
      }

      @Override
      public Void visitReturn(ReturnTree node, RuleContext ctx) {
        if (isDirectlyInFinally()) {
          ctx.report(node, "return in finally swallows any in-flight exception — the "
              + "method 'succeeds' even when the try body failed. Move it after the try.");
        }
        return super.visitReturn(node, ctx);
      }

      /** True when the nearest enclosing try-related block is a finally block. */
      // AST nodes have no value equality; node identity IS the correct comparison here.
      @SuppressWarnings("PMD.CompareObjectsWithEquals")
      private boolean isDirectlyInFinally() {
        TreePath child = getCurrentPath();
        for (TreePath p = child.getParentPath(); p != null;
            child = p, p = p.getParentPath()) {
          Tree leaf = p.getLeaf();
          if (leaf instanceof TryTree tryTree) {
            if (tryTree.getFinallyBlock() == child.getLeaf()) {
              return true;
            }
          } else if (leaf instanceof com.sun.source.tree.MethodTree
              || leaf instanceof com.sun.source.tree.LambdaExpressionTree
              || leaf instanceof com.sun.source.tree.ClassTree) {
            return false;
          }
        }
        return false;
      }
    };
  }
}
