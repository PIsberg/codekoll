package io.codekoll.rules.modern;

import com.sun.source.tree.IdentifierTree;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-STREAM-REUSE: two terminal operations on the same Stream-typed local variable — the
 * second throws {@code IllegalStateException: stream has already been operated upon}.
 */
public final class StreamReuseRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-STREAM-REUSE");

  private static final Set<String> TERMINAL_OPS = Set.of(
      "forEach", "forEachOrdered", "toArray", "reduce", "collect", "toList", "sum", "min",
      "max", "count", "average", "anyMatch", "allMatch", "noneMatch", "findFirst", "findAny",
      "iterator", "spliterator");

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
    return "Stream consumed by a second terminal operation";
  }

  @Override
  public String explanation() {
    return "A Stream is a one-shot pipeline: the first terminal operation consumes it, and "
        + "any further use throws IllegalStateException: 'stream has already been operated "
        + "upon or closed' — on every execution reaching the second call. Streams look like "
        + "collections but do not behave like them.";
  }

  @Override
  public String fix() {
    return "Collect once into a List and query the list, or re-create the stream from its "
        + "source for each pipeline.";
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

      /** Statement-order scan: count terminal ops per stream-typed local identifier. */
      private void analyze(MethodTree method, RuleContext ctx) {
        Map<String, MethodInvocationTree> firstUse = new HashMap<>();
        List<MethodInvocationTree> calls = new ArrayList<>();
        method.getBody().accept(new TreeScanner<Void, Void>() {
          @Override
          public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
            calls.add(call);
            return super.visitMethodInvocation(call, unused);
          }
        }, null);
        for (MethodInvocationTree call : calls) {
          String streamVar = terminalOpReceiver(call, ctx);
          if (streamVar != null) {
            MethodInvocationTree first = firstUse.putIfAbsent(streamVar, call);
            if (first != null) {
              ctx.report(call, "'" + streamVar + "' was already consumed by a terminal "
                  + "operation — this call throws IllegalStateException. Collect to a "
                  + "List or re-create the stream.");
            }
          }
        }
      }

      /** Returns the receiver identifier when call is a terminal op on a Stream local. */
      private @Nullable String terminalOpReceiver(MethodInvocationTree call, RuleContext ctx) {
        if (call.getMethodSelect() instanceof MemberSelectTree select
            && TERMINAL_OPS.contains(select.getIdentifier().toString())
            && select.getExpression() instanceof IdentifierTree id) {
          TreePath path = ctx.trees().getPath(ctx.unit(), select.getExpression());
          TypeMirror type = path == null ? null : ctx.typeOf(path);
          if (type != null && ctx.isSubtypeOf(type, "java.util.stream.BaseStream")) {
            return id.getName().toString();
          }
        }
        return null;
      }
    };
  }
}
