package io.codekoll.rules.correctness;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.List;

/**
 * CK-SWITCH-FALLTHROUGH: a statement-switch case with executable statements that falls into
 * the next case without break/yield/return/throw. Empty grouped cases and arrow-switch are
 * exempt.
 */
public final class SwitchFallthroughRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SWITCH-FALLTHROUGH");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Switch case falls through without break";
  }

  @Override
  public String explanation() {
    return "A colon-style case with statements but no break/return/throw continues into the "
        + "next case's body. When it is a copy-paste omission rather than intent, two "
        + "branches run for one input — and it stays hidden until the fall-through case is "
        + "actually hit.";
  }

  @Override
  public String fix() {
    return "End each case with break/return/throw; if fall-through is intended, use "
        + "arrow-form switch (case X ->) or a '// fall through' comment.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitSwitch(SwitchTree node, RuleContext ctx) {
        List<? extends CaseTree> cases = node.getCases();
        for (int i = 0; i < cases.size() - 1; i++) {
          CaseTree current = cases.get(i);
          if (current.getCaseKind() == CaseTree.CaseKind.STATEMENT
              && !current.getStatements().isEmpty()
              && !endsWithJump(current.getStatements())) {
            ctx.report(current, "This case has statements but no break/return/throw — it "
                + "falls into the next case. Add a break, or use arrow-form switch.");
          }
        }
        return super.visitSwitch(node, ctx);
      }

      private boolean endsWithJump(List<? extends com.sun.source.tree.StatementTree> stmts) {
        Tree last = stmts.get(stmts.size() - 1);
        Tree.Kind kind = last.getKind();
        return kind == Tree.Kind.BREAK || kind == Tree.Kind.RETURN
            || kind == Tree.Kind.THROW || kind == Tree.Kind.CONTINUE
            || kind == Tree.Kind.YIELD
            || (last instanceof com.sun.source.tree.BlockTree block
                && !block.getStatements().isEmpty()
                && endsWithJump(block.getStatements()));
      }
    };
  }
}
