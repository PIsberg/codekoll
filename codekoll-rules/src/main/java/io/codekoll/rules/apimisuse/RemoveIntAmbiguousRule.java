package io.codekoll.rules.apimisuse;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * CK-REMOVE-INT-AMBIGUOUS: {@code list.remove(intExpr)} on a {@code List<Integer>} —
 * overload resolution picks {@code remove(int index)}, not {@code remove(Object)}.
 */
public final class RemoveIntAmbiguousRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-REMOVE-INT-AMBIGUOUS");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "List<Integer>.remove(int) removes by INDEX, not by value";
  }

  @Override
  public String explanation() {
    return "List has remove(int index) and remove(Object element); with an int argument on "
        + "a List<Integer>, Java picks the INDEX overload. list.remove(5) deletes the "
        + "element at position 5 — not the value 5 — or throws IndexOutOfBounds. The "
        + "wrong element quietly disappears from the list.";
  }

  @Override
  public String fix() {
    return "For by-value removal box explicitly: list.remove(Integer.valueOf(5)). For "
        + "by-index removal keep the int but name it accordingly.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("remove")
            && resolvedParameterIsInt(node, ctx)
            && isIntegerList(select, ctx)) {
          ctx.report(node, "remove(int) on a List<Integer> removes by INDEX — the value "
              + "overload needs boxing: list.remove(Integer.valueOf(...)).");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean resolvedParameterIsInt(MethodInvocationTree node, RuleContext ctx) {
        return ctx.trees().getElement(new TreePath(getCurrentPath(), node))
            instanceof ExecutableElement method
            && method.getParameters().size() == 1
            && method.getParameters().get(0).asType().getKind() == TypeKind.INT;
      }

      private boolean isIntegerList(MemberSelectTree select, RuleContext ctx) {
        TypeMirror receiver =
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        return ctx.isSubtypeOf(receiver, "java.util.List")
            && receiver.toString().contains("<java.lang.Integer>");
      }
    };
  }
}
