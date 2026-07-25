package io.codekoll.rules.apimisuse;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * CK-TOARRAY-CAST: {@code (String[]) list.toArray()} — no-arg toArray returns Object[];
 * the cast throws ClassCastException every time.
 */
public final class ToArrayCastRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-TOARRAY-CAST");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.API_MISUSE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Cast of no-arg toArray() to a specific array type";
  }

  @Override
  public String explanation() {
    return "Collection.toArray() (no argument) allocates an Object[] — its runtime class is "
        + "Object[] regardless of the elements inside. Casting it to String[] throws "
        + "ClassCastException on EVERY execution; the code cannot ever have worked on this "
        + "path.";
  }

  @Override
  public String fix() {
    return "Use the typed overload: list.toArray(new String[0]) — or "
        + "list.toArray(String[]::new).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitTypeCast(TypeCastTree node, RuleContext ctx) {
        if (NullFacts.unwrap(node.getExpression()) instanceof MethodInvocationTree call
            && call.getArguments().isEmpty()
            && call.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("toArray")
            && isObjectArrayCastToSpecific(node, ctx)
            && isCollectionReceiver(select, ctx)) {
          ctx.report(node, "toArray() returns Object[] — this cast throws "
              + "ClassCastException every time. Use toArray(new T[0]).");
        }
        return super.visitTypeCast(node, ctx);
      }

      private boolean isObjectArrayCastToSpecific(TypeCastTree node, RuleContext ctx) {
        TreePath typePath = ctx.trees().getPath(ctx.unit(), node.getType());
        TypeMirror target = typePath == null ? null : ctx.typeOf(typePath);
        return target != null && target.getKind() == TypeKind.ARRAY
            && !"java.lang.Object".equals(
                ctx.qualifiedNameOf(((ArrayType) target).getComponentType()));
      }

      private boolean isCollectionReceiver(MemberSelectTree select, RuleContext ctx) {
        TypeMirror receiver =
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        return ctx.isSubtypeOf(receiver, "java.util.Collection");
      }
    };
  }
}
