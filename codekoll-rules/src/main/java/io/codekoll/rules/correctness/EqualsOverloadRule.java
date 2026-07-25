package io.codekoll.rules.correctness;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeMirror;

/**
 * CK-EQUALS-OVERLOAD: a class declares {@code equals(SomeType)} with a non-Object parameter
 * but never overrides {@code equals(Object)} — it is an overload, silently bypassed by
 * collections.
 */
public final class EqualsOverloadRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-EQUALS-OVERLOAD");

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
    return "equals(SpecificType) that overloads instead of overriding equals(Object)";
  }

  @Override
  public String explanation() {
    return "public boolean equals(MyType o) does NOT override Object.equals(Object) — it is "
        + "a new overload. Collections, Objects.equals and generic code all call "
        + "equals(Object), which still uses identity: two 'equal' objects are unequal to "
        + "every framework, and only your hand-written call sites see the intended logic.";
  }

  @Override
  public String fix() {
    return "Override equals(Object) (with @Override so the compiler checks the signature); "
        + "cast/instanceof inside, and add hashCode.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitClass(ClassTree node, RuleContext ctx) {
        if (node.getKind() == Tree.Kind.CLASS) {
          MethodTree overload = null;
          boolean hasProperOverride = false;
          for (Tree member : node.getMembers()) {
            if (member instanceof MethodTree method
                && method.getName().contentEquals("equals")
                && method.getParameters().size() == 1) {
              if (isObjectParam(method.getParameters().get(0), ctx)) {
                hasProperOverride = true;
              } else {
                overload = method;
              }
            }
          }
          if (overload != null && !hasProperOverride) {
            ctx.report(overload, "equals(" + overload.getParameters().get(0).getType()
                + ") overloads, it does not override equals(Object) — collections use the "
                + "identity version. Override equals(Object).");
          }
        }
        return super.visitClass(node, ctx);
      }

      private boolean isObjectParam(VariableTree param, RuleContext ctx) {
        TreePath typePath = ctx.trees().getPath(ctx.unit(), param.getType());
        TypeMirror type = typePath == null ? null : ctx.typeOf(typePath);
        return type != null && "java.lang.Object".equals(ctx.qualifiedNameOf(type));
      }
    };
  }
}
