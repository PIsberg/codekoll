package io.codekoll.rules.modern;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;

/**
 * CK-VT-POOLING: a virtual-thread factory handed to a bounded executor — pooling caps
 * exactly the concurrency virtual threads exist to provide.
 */
public final class VtPoolingRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-VT-POOLING");

  private static final Set<String> BOUNDED_FACTORIES = Set.of(
      "newFixedThreadPool", "newSingleThreadExecutor", "newCachedThreadPool",
      "newScheduledThreadPool", "newWorkStealingPool");

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
    return "Virtual-thread factory used with a pooled/bounded executor";
  }

  @Override
  public String explanation() {
    return "Virtual threads are cheap to create and designed to be UNPOOLED — one per "
        + "task, millions if needed. Wrapping their factory in newFixedThreadPool caps "
        + "concurrency at the pool size: the scalability virtual threads were adopted for "
        + "is silently thrown away, while the code looks 'modernized'.";
  }

  @Override
  public String fix() {
    return "Use Executors.newVirtualThreadPerTaskExecutor() — no pool, one virtual thread "
        + "per task.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && BOUNDED_FACTORIES.contains(select.getIdentifier().toString())
            && select.getExpression().toString().endsWith("Executors")
            && anyArgumentMentionsVirtualFactory(node)) {
          ctx.report(node, "Pooling virtual threads caps the unlimited concurrency they "
              + "exist to provide. Use Executors.newVirtualThreadPerTaskExecutor().");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean anyArgumentMentionsVirtualFactory(MethodInvocationTree node) {
        return node.getArguments().stream()
            .anyMatch(arg -> arg.toString().contains("ofVirtual"));
      }
    };
  }
}
