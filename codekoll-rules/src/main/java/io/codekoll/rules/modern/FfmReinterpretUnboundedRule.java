package io.codekoll.rules.modern;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
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
import org.jspecify.annotations.Nullable;

/**
 * CK-FFM-REINTERPRET-UNBOUNDED: {@code reinterpret(long)} on a raw native pointer, which gives
 * the memory a size but no owner: nothing frees it and nothing bounds its lifetime.
 */
public final class FfmReinterpretUnboundedRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-FFM-REINTERPRET-UNBOUNDED");

  private static final Set<String> DOWNCALL = Set.of("invokeExact", "invoke", "invokeWithArguments");
  private static final Set<String> POINTER_READ = Set.of("get", "getAtIndex");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Native pointer resized with reinterpret(long) but given no lifetime";
  }

  @Override
  public String explanation() {
    return "A pointer returned by native code arrives as a zero-length segment in the global "
        + "scope. reinterpret(size) makes it readable but keeps that scope: no arena owns it, so "
        + "if the native side allocated the memory (strdup, a library's create function) nothing "
        + "ever frees it, and if the native side frees it later, Java can still read it, a "
        + "use-after-free the runtime cannot catch.";
  }

  @Override
  public String fix() {
    return "Give the memory a lifetime and a cleanup: ptr.reinterpret(size, arena, MyLib::free), "
        + "so it is freed when the arena closes and unusable afterwards.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public @Nullable Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getMethodSelect() instanceof MemberSelectTree select
            && Ffm.isMethodOf(getCurrentPath(), ctx, Ffm.SEGMENT, Set.of("reinterpret"))
            && isNativePointer(select.getExpression(), getCurrentPath())) {
          ctx.report(node, "reinterpret(size) on a native pointer keeps its global scope: no "
              + "arena owns this memory, so nothing frees it or stops use after native code frees "
              + "it. Use reinterpret(size, arena, cleanup).");
        }
        return super.visitMethodInvocation(node, ctx);
      }
    };
  }

  private static boolean isNativePointer(ExpressionTree receiver, TreePath at) {
    ExpressionTree expression = Ffm.unwrap(receiver);
    if (expression instanceof IdentifierTree id) {
      @Nullable BlockTree body = Ffm.enclosingMethodBody(at);
      @Nullable ExpressionTree init =
          body == null ? null : Ffm.localInitializer(body, id.getName().toString());
      return init != null && isPointerSource(Ffm.unwrap(init));
    }
    return isPointerSource(expression);
  }

  /** A downcall result, {@code MemorySegment.ofAddress}, or a read with an ADDRESS layout. */
  private static boolean isPointerSource(ExpressionTree expression) {
    if (expression instanceof MethodInvocationTree call
        && call.getMethodSelect() instanceof MemberSelectTree select) {
      String name = select.getIdentifier().toString();
      return DOWNCALL.contains(name)
          || "ofAddress".equals(name)
          || POINTER_READ.contains(name) && !call.getArguments().isEmpty()
              && call.getArguments().getFirst().toString().contains("ADDRESS");
    }
    return false;
  }
}
