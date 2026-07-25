package io.codekoll.rules.modern;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

/**
 * CK-SEALED-SWITCH-DEFAULT: a {@code default} branch in a switch over a sealed type defeats
 * the compiler's exhaustiveness checking — a future permitted subtype routes to default
 * instead of failing compilation.
 */
public final class SealedSwitchDefaultRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SEALED-SWITCH-DEFAULT");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.MODERN;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "default branch in a switch over a sealed type";
  }

  @Override
  public String explanation() {
    return "Sealed types exist so the compiler can prove a switch covers every permitted "
        + "subtype — adding one later turns unhandled cases into compile ERRORS. A default "
        + "branch forfeits exactly that: the new subtype silently routes to default, and "
        + "the compiler that would have pointed at every switch to update stays quiet.";
  }

  @Override
  public String fix() {
    return "Delete the default branch and enumerate the permitted subtypes; the compiler "
        + "then enforces completeness forever.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitSwitch(SwitchTree node, RuleContext ctx) {
        check(node, node.getExpression(), node.getCases(), ctx);
        return super.visitSwitch(node, ctx);
      }

      @Override
      public Void visitSwitchExpression(SwitchExpressionTree node, RuleContext ctx) {
        check(node, node.getExpression(), node.getCases(), ctx);
        return super.visitSwitchExpression(node, ctx);
      }

      private void check(Tree node, com.sun.source.tree.ExpressionTree selector,
          List<? extends CaseTree> cases, RuleContext ctx) {
        boolean hasDefault = cases.stream()
            .anyMatch(c -> c.getLabels().stream()
                .anyMatch(l -> l.getKind() == Tree.Kind.DEFAULT_CASE_LABEL));
        if (hasDefault && isSealedSelector(selector, ctx)) {
          ctx.report(node, "default on a sealed-type switch disables exhaustiveness "
              + "checking — a future subtype silently falls into it. Enumerate the "
              + "permitted subtypes instead.");
        }
      }

      private boolean isSealedSelector(com.sun.source.tree.ExpressionTree selector,
          RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), selector));
        if (type == null) {
          return false;
        }
        Element element = ctx.types().asElement(type);
        return element != null && element.getModifiers().contains(Modifier.SEALED);
      }
    };
  }
}
