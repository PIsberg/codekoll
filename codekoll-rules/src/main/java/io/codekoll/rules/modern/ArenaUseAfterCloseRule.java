package io.codekoll.rules.modern;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeMirror;

/**
 * CK-ARENA-USE-AFTER-CLOSE: an FFM {@code MemorySegment} used, in statement order, after its
 * {@code Arena.close()} — IllegalStateException (use-after-free).
 */
public final class ArenaUseAfterCloseRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-ARENA-USE-AFTER-CLOSE");

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
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "MemorySegment used after its Arena is closed";
  }

  @Override
  public String explanation() {
    return "Closing an Arena frees every MemorySegment it allocated. Any access to such a "
        + "segment afterwards throws IllegalStateException: 'Already closed' — the "
        + "foreign-memory equivalent of a use-after-free, caught by the runtime instead of "
        + "corrupting memory, but a guaranteed crash all the same.";
  }

  @Override
  public String fix() {
    return "Keep all segment use inside the arena's lifetime — ideally a try-with-resources "
        + "arena (try (Arena a = Arena.ofConfined()) { ... }) so use cannot escape close.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethod(MethodTree node, RuleContext ctx) {
        if (node.getBody() != null) {
          analyze(node, ctx);
        }
        return super.visitMethod(node, ctx);
      }

      private void analyze(MethodTree method, RuleContext ctx) {
        java.util.List<MethodInvocationTree> calls = new java.util.ArrayList<>();
        method.getBody().accept(new TreeScanner<Void, Void>() {
          @Override
          public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
            calls.add(call);
            return super.visitMethodInvocation(call, unused);
          }
        }, null);
        String closedArena = null;
        for (MethodInvocationTree call : calls) {
          String arena = arenaClosed(call, ctx);
          if (arena != null) {
            closedArena = arena;
          } else if (closedArena != null && usesSegmentOf(call, ctx)) {
            ctx.report(call, "MemorySegment accessed after its Arena was closed — "
                + "IllegalStateException (use-after-free). Keep segment use inside the "
                + "arena's scope.");
          }
        }
      }

      private String arenaClosed(MethodInvocationTree call, RuleContext ctx) {
        if (call.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("close")) {
          TypeMirror receiver =
              ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
          if ("java.lang.foreign.Arena".equals(ctx.qualifiedNameOf(receiver))) {
            return select.getExpression().toString();
          }
        }
        return null;
      }

      private boolean usesSegmentOf(MethodInvocationTree call, RuleContext ctx) {
        if (call.getMethodSelect() instanceof MemberSelectTree select) {
          TypeMirror receiver =
              ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
          return "java.lang.foreign.MemorySegment".equals(ctx.qualifiedNameOf(receiver));
        }
        return false;
      }
    };
  }
}
