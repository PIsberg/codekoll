package io.codekoll.rules.correctness;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.element.Element;
import org.jspecify.annotations.Nullable;

/**
 * CK-SELF-ASSIGN: assignment of a variable to itself — {@code x = x}, {@code this.f = f}
 * where both sides resolve to the same symbol. Almost always a typo for a shadowed
 * parameter ({@code this.x = x} with a missing parameter is the classic constructor bug).
 */
public final class SelfAssignRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SELF-ASSIGN");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.CORRECTNESS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Variable assigned to itself";
  }

  @Override
  public String explanation() {
    return "Both sides of the assignment resolve to the same variable, so the statement does "
        + "nothing. In constructors this is usually 'this.name = name' typed while the "
        + "parameter is missing or misspelled — the field silently keeps its default value "
        + "(null/0) and the bug appears far away, as an NPE at first use.";
  }

  @Override
  public String fix() {
    return "Assign from the intended source — usually a parameter of the same name "
        + "(this.name = name with the parameter actually declared), or delete the statement.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitAssignment(AssignmentTree node, RuleContext ctx) {
        Element left = resolve(node.getVariable(), ctx);
        Element right = resolve(node.getExpression(), ctx);
        if (left != null && left.equals(right)) {
          ctx.report(node, "'" + node.getVariable() + " = " + node.getExpression()
              + "' assigns the variable to itself — no effect. Did you mean a parameter "
              + "or another field?");
        }
        return super.visitAssignment(node, ctx);
      }

      /** Resolves identifiers and this.f selects; anything else (calls, arrays) → null. */
      private @Nullable Element resolve(ExpressionTree expr, RuleContext ctx) {
        if (expr instanceof IdentifierTree
            || (expr instanceof MemberSelectTree select
                && select.getExpression().getKind() == Tree.Kind.IDENTIFIER
                && "this".equals(select.getExpression().toString()))) {
          return ctx.trees().getElement(new TreePath(getCurrentPath(), expr));
        }
        return null;
      }
    };
  }
}
