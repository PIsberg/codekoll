package io.codekoll.rules.nullness;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.NullFacts.Fact;
import io.codekoll.rules.support.RuleContext;
import java.util.List;

/**
 * CK-NON-SHORT-CIRCUIT: boolean {@code &}/{@code |} where one side null-guards a variable
 * the other side dereferences — both sides ALWAYS evaluate, so the guard doesn't guard.
 */
public final class NonShortCircuitRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-NON-SHORT-CIRCUIT");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.NULLNESS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Non-short-circuit &/| defeats a null guard";
  }

  @Override
  public String explanation() {
    return "& and | (single) evaluate BOTH operands unconditionally — unlike && and ||. "
        + "In 'x != null & x.length() > 0' the dereference runs even when x is null: "
        + "the guard that reads correctly is one keystroke away from a guaranteed "
        + "NullPointerException on the null path.";
  }

  @Override
  public String fix() {
    return "Use the short-circuit operators && / || for guarded evaluation; keep & / | "
        + "for bit arithmetic only.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      @SuppressWarnings("PMD.CompareObjectsWithEquals")
      public Void visitBinary(BinaryTree node, RuleContext ctx) {
        if (node.getKind() == Tree.Kind.AND || node.getKind() == Tree.Kind.OR) {
          List<ExpressionTree> operands = NullFacts.flatten(node, node.getKind());
          // Any null-fact on x in one operand + a dereference of x in ANY other operand
          // is unsafe: with non-short-circuit evaluation, order gives no protection.
          // (operand identity comparison below is AST-node identity — intentional.)
          for (ExpressionTree guard : operands) {
            Fact fact = NullFacts.factOf(guard);
            if (fact == null) {
              continue;
            }
            for (ExpressionTree other : operands) {
              if (other != guard
                  && NullFacts.dereferencedIdentifiers(other).contains(fact.name())) {
                ctx.report(node, "'" + fact.name() + "' is null-checked on one side of "
                    + (node.getKind() == Tree.Kind.AND ? "&" : "|")
                    + " and dereferenced on the other — BOTH sides always evaluate. "
                    + "Use " + (node.getKind() == Tree.Kind.AND ? "&&" : "||") + ".");
                return super.visitBinary(node, ctx);
              }
            }
          }
        }
        return super.visitBinary(node, ctx);
      }
    };
  }
}
