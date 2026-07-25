package io.codekoll.rules.concurrency;

import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-CONCURRENT-MOD: inside an enhanced-for over collection {@code c}, a structural
 * modification of the SAME collection ({@code c.add/remove/clear}) — guaranteed
 * ConcurrentModificationException.
 */
public final class ConcurrentModRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-CONCURRENT-MOD");

  private static final Set<String> MUTATORS =
      Set.of("add", "remove", "clear", "addAll", "removeAll", "retainAll", "put");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.CONCURRENCY;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Collection modified while iterating it with for-each";
  }

  @Override
  public String explanation() {
    return "for-each uses the collection's iterator, which fail-fasts: structurally "
        + "modifying the same collection during the loop throws "
        + "ConcurrentModificationException on the next iteration. The 'remove matching "
        + "items' loop crashes as soon as it finds one.";
  }

  @Override
  public String fix() {
    return "Use Iterator.remove(), Collection.removeIf(predicate), or collect the items to "
        + "change and apply them after the loop.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitEnhancedForLoop(EnhancedForLoopTree node, RuleContext ctx) {
        if (NullFacts.unwrap(node.getExpression())
            instanceof com.sun.source.tree.IdentifierTree iterable
            && isCollection(node.getExpression(), ctx)) {
          String collection = iterable.getName().toString();
          MethodInvocationTree mutation = findMutation(node, collection);
          if (mutation != null) {
            ctx.report(mutation, "Modifying '" + collection + "' while iterating it throws "
                + "ConcurrentModificationException. Use Iterator.remove or removeIf.");
          }
        }
        return super.visitEnhancedForLoop(node, ctx);
      }

      private boolean isCollection(com.sun.source.tree.ExpressionTree expr, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), expr));
        return ctx.isSubtypeOf(type, "java.util.Collection")
            || ctx.isSubtypeOf(type, "java.util.Map");
      }

      private @Nullable MethodInvocationTree findMutation(EnhancedForLoopTree loop,
          String collection) {
        return loop.getStatement().accept(new TreeScanner<MethodInvocationTree, Void>() {
          @Override
          public MethodInvocationTree visitMethodInvocation(MethodInvocationTree call,
              Void unused) {
            if (call.getMethodSelect() instanceof MemberSelectTree select
                && MUTATORS.contains(select.getIdentifier().toString())
                && select.getExpression().toString().equals(collection)) {
              return call;
            }
            return super.visitMethodInvocation(call, unused);
          }

          @Override
          public MethodInvocationTree reduce(@Nullable MethodInvocationTree a,
              @Nullable MethodInvocationTree b) {
            return a != null ? a : b;
          }
        }, null);
      }
    };
  }
}
