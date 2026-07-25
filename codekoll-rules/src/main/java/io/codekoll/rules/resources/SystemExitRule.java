package io.codekoll.rules.resources;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;

/**
 * CK-SYSTEM-EXIT: {@code System.exit} outside a main-bearing/launcher class — library code
 * killing the whole JVM takes the host application down with it.
 */
public final class SystemExitRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SYSTEM-EXIT");

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
    return "System.exit in non-launcher code";
  }

  @Override
  public String explanation() {
    return "System.exit terminates the ENTIRE JVM: every request in flight, every other "
        + "component, the application server hosting the library. An error path that "
        + "exits instead of throwing turns one failed operation into a full outage — and "
        + "skips finally blocks and try-with-resources cleanup on other threads.";
  }

  @Override
  public String fix() {
    return "Throw an exception and let the actual entry point decide the process's fate; "
        + "keep System.exit in main/launcher classes.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && (select.getIdentifier().contentEquals("exit")
                || select.getIdentifier().contentEquals("halt"))
            && (select.getExpression().toString().endsWith("System")
                || select.getExpression().toString().contains("Runtime"))
            && !inLauncherClass()) {
          ctx.report(node, "System.exit kills the whole JVM — every in-flight request "
              + "included. Throw instead; only the entry point decides process fate.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean inLauncherClass() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          if (p.getLeaf() instanceof ClassTree cls) {
            String name = cls.getSimpleName().toString();
            if (name.endsWith("Main") || name.endsWith("Launcher") || name.endsWith("Cli")
                || name.endsWith("App") || hasMainMethod(cls)) {
              return true;
            }
          }
        }
        return false;
      }

      private boolean hasMainMethod(ClassTree cls) {
        for (Tree member : cls.getMembers()) {
          if (member instanceof MethodTree method
              && method.getName().contentEquals("main")
              && method.getParameters().size() == 1) {
            return true;
          }
        }
        return false;
      }
    };
  }
}
