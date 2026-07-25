package io.codekoll.rules.correctness;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeMirror;

/**
 * CK-COLLECTION-SELF-ADD: {@code c.add(c)} / {@code c.addAll(c)} — a self-containing
 * collection makes hashCode/toString recurse to StackOverflowError.
 */
public final class CollectionSelfAddRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-COLLECTION-SELF-ADD");

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
    return "Collection added to itself (self-referential structure)";
  }

  @Override
  public String explanation() {
    return "A collection that contains itself makes hashCode() and toString() recurse "
        + "forever — the first call to either (often deep in a logging or equals path) "
        + "throws StackOverflowError. add(self)/addAll(self) is never intentional.";
  }

  @Override
  public String fix() {
    return "Add the intended element or a different collection; a collection must not "
        + "contain itself.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getMethodSelect() instanceof MemberSelectTree select
            && (select.getIdentifier().contentEquals("add")
                || select.getIdentifier().contentEquals("addAll"))
            && select.getExpression().toString()
                .equals(node.getArguments().get(0).toString())
            && isCollection(select.getExpression(), ctx)) {
          ctx.report(node, "A collection added to itself makes hashCode/toString recurse "
              + "to StackOverflowError. Add the intended element instead.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isCollection(com.sun.source.tree.ExpressionTree receiver,
          RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), receiver));
        return ctx.isSubtypeOf(type, "java.util.Collection");
      }
    };
  }
}
