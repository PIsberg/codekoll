package io.codekoll.rules.frameworks;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * CK-TX-LAZY-RETURN: a {@code @Transactional} method returning a lazy result (a {@code Stream},
 * a {@code StreamingResponseBody}) that is consumed after the transaction has closed.
 */
public final class TxLazyReturnRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-TX-LAZY-RETURN");

  private static final Set<String> LAZY = Set.of(
      "Stream", "IntStream", "LongStream", "DoubleStream", "StreamingResponseBody");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.FRAMEWORKS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "@Transactional method returns a lazy Stream or StreamingResponseBody";
  }

  @Override
  public String explanation() {
    return "The transaction commits and the persistence context closes when a @Transactional "
        + "method returns. A Stream or StreamingResponseBody it returns is only consumed after "
        + "that, so a streaming query reads from a closed ResultSet or session, and any lazy "
        + "association touched while consuming throws LazyInitializationException. The method's "
        + "own tests pass whenever they call it inside a test transaction.";
  }

  @Override
  public String fix() {
    return "Collect inside the method (toList()), or move @Transactional to the caller that "
        + "consumes the stream, inside a try-with-resources.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public @Nullable Void visitMethod(MethodTree node, RuleContext ctx) {
        if (node.getReturnType() != null && node.getBody() != null
            && LAZY.contains(rawSimpleName(node.getReturnType().toString()))
            && Annotations.find(node.getModifiers(), "Transactional").isPresent()
            && !allReturnsInMemory(getCurrentPath(), ctx)) {
          ctx.report(node.getReturnType(), "This " + rawSimpleName(node.getReturnType().toString())
              + " is consumed after the @Transactional method returns and its transaction has "
              + "closed. Collect it here (toList()), or open the transaction where it is consumed.");
        }
        return super.visitMethod(node, ctx);
      }
    };
  }

  private static String rawSimpleName(String type) {
    String raw = type.contains("<") ? type.substring(0, type.indexOf('<')) : type;
    return raw.substring(raw.lastIndexOf('.') + 1);
  }

  /** Every {@code return} of the method (not of nested lambdas/classes) is an in-memory stream. */
  private static boolean allReturnsInMemory(TreePath methodPath, RuleContext ctx) {
    MethodTree method = (MethodTree) methodPath.getLeaf();
    List<TreePath> returned = new ArrayList<>();
    new TreePathScanner<Void, Void>() {
      @Override
      public @Nullable Void visitReturn(ReturnTree node, Void unused) {
        if (node.getExpression() != null) {
          returned.add(new TreePath(getCurrentPath(), node.getExpression()));
        }
        return super.visitReturn(node, unused);
      }

      @Override
      public @Nullable Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
        return null;
      }

      @Override
      public @Nullable Void visitClass(ClassTree node, Void unused) {
        return null;
      }
    }.scan(new TreePath(methodPath, method.getBody()), null);
    return !returned.isEmpty() && returned.stream().allMatch(path -> inMemory(path, ctx));
  }

  /** {@code collection.stream()}, {@code Stream.of(...)}, {@code Arrays.stream(...)}. */
  private static boolean inMemory(TreePath path, RuleContext ctx) {
    ExpressionTree expression = (ExpressionTree) path.getLeaf();
    if (expression instanceof ConditionalExpressionTree conditional) {
      return inMemory(new TreePath(path, conditional.getTrueExpression()), ctx)
          && inMemory(new TreePath(path, conditional.getFalseExpression()), ctx);
    }
    if (expression instanceof MethodInvocationTree call
        && call.getMethodSelect() instanceof MemberSelectTree select) {
      String name = select.getIdentifier().toString();
      String receiver = select.getExpression().toString();
      return "stream".equals(name) && call.getArguments().isEmpty()
              && ctx.isSubtypeOf(ctx.typeOf(new TreePath(path, select.getExpression())),
                  "java.util.Collection")
          || "Stream".equals(receiver) && "of".equals(name)
          || "Arrays".equals(receiver) && "stream".equals(name);
    }
    return false;
  }
}
