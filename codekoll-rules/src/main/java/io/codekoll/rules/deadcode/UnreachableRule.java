package io.codekoll.rules.deadcode;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.Jumps;
import io.codekoll.rules.support.RuleContext;
import java.util.List;

/** CK-UNREACHABLE: the statement after a literal-true {@code if} whose then-branch always exits. */
public final class UnreachableRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-UNREACHABLE");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Statement after an if (true) that always exits";
  }

  @Override
  public String explanation() {
    return "When an if with a literal-true condition and no else ends in return, throw, break, "
        + "continue or yield, the statement after it can never run. It is usually a leftover "
        + "from debugging or a mistaken condition.";
  }

  @Override
  public String fix() {
    return "Remove the unreachable statement, or correct the condition.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitBlock(BlockTree node, RuleContext ctx) {
        check(node.getStatements(), ctx);
        return super.visitBlock(node, ctx);
      }

      @Override
      public Void visitCase(CaseTree node, RuleContext ctx) {
        if (node.getCaseKind() == CaseTree.CaseKind.STATEMENT && node.getStatements() != null) {
          check(node.getStatements(), ctx);
        }
        return super.visitCase(node, ctx);
      }

      private void check(List<? extends StatementTree> stmts, RuleContext ctx) {
        for (int i = 0; i < stmts.size() - 1; i++) {
          if (stmts.get(i) instanceof IfTree ifTree && ifTree.getElseStatement() == null
              && LiteralConditions.valueOf(ifTree.getCondition()) == LiteralConditions.Truth.TRUE
              && Jumps.endsWithJump(List.of(ifTree.getThenStatement()))) {
            StatementTree next = stmts.get(i + 1);
            String text = next.toString().lines().findFirst().orElse("").strip();
            if (text.length() > 60) {
              text = text.substring(0, 60) + "...";
            }
            ctx.report(next, "Unreachable statement '" + text
                + "': the preceding if (true) always exits");
          }
        }
      }
    };
  }
}
