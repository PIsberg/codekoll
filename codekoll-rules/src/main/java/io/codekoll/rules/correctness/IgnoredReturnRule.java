package io.codekoll.rules.correctness;

import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;

/**
 * CK-IGNORED-RETURN: a value-returning method on a known-immutable type invoked as a bare
 * statement — the result (the entire point of the call) is thrown away.
 *
 * <p>The known-pure receiver list covers types whose value-returning methods have no side
 * effects: String, BigDecimal/BigInteger, java.time, Optional, Path. Mutating-but-fluent
 * methods (StringBuilder.append, Map.put, …) are naturally exempt because their receivers
 * are not on the list.
 */
public final class IgnoredReturnRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-IGNORED-RETURN");

  /** Receiver types whose non-void methods are pure (result-discarding is always a bug). */
  private static final Set<String> PURE_TYPES = Set.of(
      "java.lang.String",
      "java.math.BigDecimal",
      "java.math.BigInteger",
      "java.time.LocalDate",
      "java.time.LocalTime",
      "java.time.LocalDateTime",
      "java.time.ZonedDateTime",
      "java.time.OffsetDateTime",
      "java.time.Instant",
      "java.time.Duration",
      "java.time.Period",
      "java.util.Optional",
      "java.nio.file.Path");

  /** Methods on pure types that are legitimately invoked for effect. */
  private static final Set<String> EFFECT_METHODS = Set.of(
      "orElseThrow",   // Optional: side effect = throw
      "ifPresent", "ifPresentOrElse",  // void anyway, listed for clarity
      "getChars", "chars", "wait", "notify", "notifyAll");

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
    return "Return value of a pure method on an immutable type is discarded";
  }

  @Override
  public String explanation() {
    return "Immutable types like String, BigDecimal and LocalDate never modify themselves — "
        + "their methods return a NEW object. Calling name.trim() as a bare statement "
        + "computes the trimmed string and throws it away; the original is unchanged and "
        + "the program continues with the un-trimmed value.";
  }

  @Override
  public String fix() {
    return "Assign the result: name = name.trim(); — or use it directly in the enclosing "
        + "expression.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitExpressionStatement(ExpressionStatementTree node, RuleContext ctx) {
        if (node.getExpression() instanceof MethodInvocationTree call) {
          Element element = ctx.trees().getElement(
              new TreePath(getCurrentPath(), call));
          if (element instanceof ExecutableElement method
              && method.getReturnType().getKind() != TypeKind.VOID
              && !EFFECT_METHODS.contains(method.getSimpleName().toString())
              && isPureReceiver(method)) {
            String receiver = simpleReceiverName(call);
            ctx.report(node, receiver + "." + method.getSimpleName()
                + "() returns a new value; the result is discarded — the receiver is "
                + "immutable and unchanged. Assign or use the result.");
          }
        }
        return super.visitExpressionStatement(node, ctx);
      }

      private boolean isPureReceiver(ExecutableElement method) {
        Element owner = method.getEnclosingElement();
        return owner instanceof javax.lang.model.element.TypeElement type
            && PURE_TYPES.contains(type.getQualifiedName().toString());
      }

      private String simpleReceiverName(MethodInvocationTree call) {
        if (call.getMethodSelect() instanceof MemberSelectTree select) {
          String text = select.getExpression().toString();
          return text.length() > 20 ? "expression" : text;
        }
        return "this";
      }
    };
  }
}
