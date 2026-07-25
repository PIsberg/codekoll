package io.codekoll.rules.correctness;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.element.Element;

/**
 * CK-INFINITE-RECURSION: a method whose FIRST statement unconditionally calls itself with
 * the same arguments — guaranteed StackOverflowError. Only the unconditional case fires.
 */
public final class InfiniteRecursionRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-INFINITE-RECURSION");

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
    return "Method unconditionally calls itself (infinite recursion)";
  }

  @Override
  public String explanation() {
    return "The method's first action is to call itself with the same arguments, with no "
        + "branch in between — so every call makes another identical call. The result is "
        + "StackOverflowError on the very first invocation, a common accidental-delegation "
        + "or getter/setter typo.";
  }

  @Override
  public String fix() {
    return "Call the intended target — the super method, the field, the collaborator — not "
        + "this method again; or add the missing base case.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethod(MethodTree node, RuleContext ctx) {
        if (node.getBody() != null) {
          StatementTree first = firstStatement(node.getBody());
          MethodInvocationTree call = selfCall(first);
          if (call != null && callsSameMethod(call, ctx)
              && sameArity(call, node)) {
            ctx.report(call, "This method's first statement calls itself unconditionally — "
                + "guaranteed StackOverflowError. Call the intended target instead.");
          }
        }
        return super.visitMethod(node, ctx);
      }

      private StatementTree firstStatement(BlockTree body) {
        return body.getStatements().isEmpty() ? null : body.getStatements().get(0);
      }

      private MethodInvocationTree selfCall(StatementTree statement) {
        ExpressionTree expr = null;
        if (statement instanceof ReturnTree ret) {
          expr = ret.getExpression();
        } else if (statement instanceof com.sun.source.tree.ExpressionStatementTree es) {
          expr = es.getExpression();
        }
        return expr != null && NullFacts.unwrap(expr) instanceof MethodInvocationTree call
            ? call : null;
      }

      private boolean callsSameMethod(MethodInvocationTree call, RuleContext ctx) {
        // Unqualified name or this.name — same instance method.
        ExpressionTree select = call.getMethodSelect();
        boolean selfTargeted = !(select instanceof MemberSelectTree ms)
            || "this".equals(ms.getExpression().toString());
        if (!selfTargeted) {
          return false;
        }
        Element called = ctx.trees().getElement(new TreePath(getCurrentPath(), call));
        Element enclosing = enclosingMethodElement(ctx);
        return called != null && called.equals(enclosing);
      }

      private Element enclosingMethodElement(RuleContext ctx) {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          if (p.getLeaf() instanceof MethodTree) {
            return ctx.trees().getElement(p);
          }
        }
        return null;
      }

      private boolean sameArity(MethodInvocationTree call, MethodTree method) {
        return call.getArguments().size() == method.getParameters().size();
      }
    };
  }
}
