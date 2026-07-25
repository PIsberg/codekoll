package io.codekoll.rules.correctness;

import com.sun.source.tree.IfTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;

/**
 * CK-ASSIGN-IN-COND: a bare assignment as an if/while condition — {@code if (done = true)}
 * assigns and always takes the branch. The comparison-wrapped read-loop idiom
 * {@code while ((line = r.readLine()) != null)} is naturally exempt (the condition's top
 * node is a comparison, not an assignment).
 */
public final class AssignInCondRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-ASSIGN-IN-COND");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Assignment (=) used directly as a boolean condition";
  }

  @Override
  public String explanation() {
    return "if (done = true) ASSIGNS true and then branches on it — the condition is "
        + "always true and the variable is clobbered as a side effect. One '=' short of "
        + "the intended comparison, and perfectly legal Java when the variable is boolean.";
  }

  @Override
  public String fix() {
    return "Use == for comparison (or drop the comparison entirely: if (done)). Keep "
        + "assignments out of conditions unless wrapped in an explicit comparison.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitIf(IfTree node, RuleContext ctx) {
        check(node.getCondition(), ctx);
        return super.visitIf(node, ctx);
      }

      @Override
      public Void visitWhileLoop(com.sun.source.tree.WhileLoopTree node, RuleContext ctx) {
        check(node.getCondition(), ctx);
        return super.visitWhileLoop(node, ctx);
      }

      @Override
      public Void visitDoWhileLoop(com.sun.source.tree.DoWhileLoopTree node, RuleContext ctx) {
        check(node.getCondition(), ctx);
        return super.visitDoWhileLoop(node, ctx);
      }

      private void check(com.sun.source.tree.ExpressionTree condition, RuleContext ctx) {
        com.sun.source.tree.ExpressionTree top = NullFacts.unwrap(condition);
        if (top.getKind() == Tree.Kind.ASSIGNMENT) {
          ctx.report(condition, "This ASSIGNS and then branches on the assigned value — "
              + "always the same outcome, variable clobbered. Did you mean == ?");
        }
      }
    };
  }
}
