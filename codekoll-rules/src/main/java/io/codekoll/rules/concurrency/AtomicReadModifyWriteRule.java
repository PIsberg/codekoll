package io.codekoll.rules.concurrency;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;

/**
 * CK-ATOMIC-READ-MODIFY-WRITE: {@code counter.set(counter.get() + 1)} — a read and a write
 * where the {@code Atomic} type offers one indivisible operation. Two threads can read the
 * same value and one update is silently lost.
 */
public final class AtomicReadModifyWriteRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-ATOMIC-READ-MODIFY-WRITE");

  private static final Set<String> WRITE_METHODS = Set.of("set", "lazySet");

  private static final List<String> ATOMIC_TYPES = List.of(
      "java.util.concurrent.atomic.AtomicInteger",
      "java.util.concurrent.atomic.AtomicLong",
      "java.util.concurrent.atomic.AtomicBoolean",
      "java.util.concurrent.atomic.AtomicReference");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Atomic updated by set(get() ...) — a two-step read-modify-write, not an atomic one";
  }

  @Override
  public String explanation() {
    return "counter.set(counter.get() + 1) is two separate atomic operations with a gap "
        + "between them, not one atomic update. Two threads can read the same value, both add "
        + "one, and both write the same result: one increment disappears. Nothing throws and "
        + "no test fails — the count is simply a little low under load, which is precisely the "
        + "bug the Atomic type was chosen to prevent. Holding a lock across both steps does "
        + "make it correct, but then the Atomic is not what is providing the safety.";
  }

  @Override
  public String fix() {
    return "Use the one-call form: incrementAndGet(), addAndGet(delta), or the general "
        + "updateAndGet(current -> ...) / accumulateAndGet(...), which retry on contention "
        + "instead of losing the update.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        check(node, ctx);
        return super.visitMethodInvocation(node, ctx);
      }

      private void check(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() != 1
            || !(node.getMethodSelect() instanceof MemberSelectTree select)
            || !WRITE_METHODS.contains(select.getIdentifier().toString())) {
          return;
        }
        Element atomic =
            ctx.trees().getElement(new TreePath(getCurrentPath(), select.getExpression()));
        if (atomic == null || !isAtomic(atomic, ctx) || underLock(getCurrentPath())) {
          return;
        }
        TreePath argument = new TreePath(getCurrentPath(), node.getArguments().get(0));
        if (readsSameAtomic(atomic, argument, ctx)) {
          String name = atomic.getSimpleName().toString();
          ctx.report(node, name + ".set(" + name + ".get() ...) is a read and a write with a "
              + "gap between them — two threads can read the same value and one update is "
              + "lost, which is what this Atomic exists to prevent. Use incrementAndGet(), "
              + "addAndGet(...), or updateAndGet(current -> ...).");
        }
      }

      /** True when the new value is computed from {@code atomic.get()} on the same variable. */
      private boolean readsSameAtomic(Element atomic, TreePath argument, RuleContext ctx) {
        boolean[] found = {false};
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            if (node.getArguments().isEmpty()
                && node.getMethodSelect() instanceof MemberSelectTree select
                && "get".equals(select.getIdentifier().toString())
                && atomic.equals(ctx.trees()
                    .getElement(new TreePath(getCurrentPath(), select.getExpression())))) {
              found[0] = true;
            }
            return super.visitMethodInvocation(node, unused);
          }
        }.scan(argument, null);
        return found[0];
      }

      /**
       * True when a lock already makes the two steps indivisible: a synchronized block, or a
       * synchronized method, between the call and its enclosing class.
       */
      private boolean underLock(TreePath start) {
        for (TreePath p = start; p != null; p = p.getParentPath()) {
          Tree tree = p.getLeaf();
          if (tree instanceof SynchronizedTree) {
            return true;
          }
          if (tree instanceof MethodTree method) {
            return method.getModifiers().getFlags().contains(Modifier.SYNCHRONIZED);
          }
          if (tree instanceof ClassTree) {
            return false;
          }
        }
        return false;
      }

      private boolean isAtomic(@Nullable Element element, RuleContext ctx) {
        return element != null
            && ATOMIC_TYPES.stream().anyMatch(fqn -> ctx.isSubtypeOf(element.asType(), fqn));
      }
    };
  }
}
