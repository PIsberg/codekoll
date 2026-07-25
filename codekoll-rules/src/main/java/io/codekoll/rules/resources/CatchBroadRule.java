package io.codekoll.rules.resources;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-CATCH-BROAD: {@code catch (Throwable)} / {@code catch (Error)} swallows
 * OutOfMemoryError, StackOverflowError and linkage errors. Exemptions: the block rethrows
 * the caught variable, or the enclosing class looks like a framework top-level
 * (*Runner/*Executor/*Loop).
 */
public final class CatchBroadRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-CATCH-BROAD");

  private static final Set<String> BROAD_TYPES =
      Set.of("java.lang.Throwable", "java.lang.Error");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.RESOURCES;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "catch (Throwable/Error) also swallows JVM errors";
  }

  @Override
  public String explanation() {
    return "Throwable and Error include OutOfMemoryError, StackOverflowError and class "
        + "linkage failures — states the application cannot meaningfully recover from. "
        + "Catching them keeps a half-broken JVM limping along, turning one clear crash "
        + "into hours of confusing downstream corruption.";
  }

  @Override
  public String fix() {
    return "Catch the specific exceptions you can handle (Exception at the broadest); if a "
        + "framework top-level must catch Throwable, rethrow Errors after logging.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitCatch(CatchTree node, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(
            new TreePath(getCurrentPath(), node.getParameter().getType()));
        if (BROAD_TYPES.contains(ctx.qualifiedNameOf(type))
            && !rethrowsCaught(node)
            && !isFrameworkTopLevel()) {
          ctx.report(node, "catch (" + node.getParameter().getType() + ") swallows "
              + "OutOfMemoryError/StackOverflowError too. Catch Exception (or narrower), "
              + "or rethrow Errors.");
        }
        return super.visitCatch(node, ctx);
      }

      private boolean rethrowsCaught(CatchTree node) {
        String name = node.getParameter().getName().toString();
        Boolean found = node.getBlock().accept(new TreeScanner<Boolean, Void>() {
          @Override
          public Boolean visitThrow(ThrowTree throwTree, Void unused) {
            if (throwTree.getExpression() instanceof IdentifierTree id
                && id.getName().contentEquals(name)) {
              return Boolean.TRUE;
            }
            return super.visitThrow(throwTree, unused);
          }

          @Override
          public Boolean reduce(@Nullable Boolean a, @Nullable Boolean b) {
            return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
          }
        }, null);
        return Boolean.TRUE.equals(found);
      }

      private boolean isFrameworkTopLevel() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          if (p.getLeaf() instanceof com.sun.source.tree.ClassTree cls) {
            String name = cls.getSimpleName().toString();
            return name.endsWith("Runner") || name.endsWith("Executor")
                || name.endsWith("Loop");
          }
        }
        return false;
      }
    };
  }
}
