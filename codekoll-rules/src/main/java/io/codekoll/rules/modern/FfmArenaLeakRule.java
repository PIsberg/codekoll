package io.codekoll.rules.modern;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import org.jspecify.annotations.Nullable;

/**
 * CK-FFM-ARENA-LEAK: a confined or shared {@code Arena} that is never closed, so its off-heap
 * memory stays reserved until the process exits.
 */
public final class FfmArenaLeakRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-FFM-ARENA-LEAK");

  private static final Set<String> CLOSABLE = Set.of("ofConfined", "ofShared");

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
    return "Confined or shared Arena never closed";
  }

  @Override
  public String explanation() {
    return "Memory allocated from Arena.ofConfined() or Arena.ofShared() is off-heap and is "
        + "released only by close(). The garbage collector does not free it, even when the arena "
        + "object itself becomes unreachable, so an arena created per call and never closed "
        + "leaks every segment allocated from it until the process runs out of native memory.";
  }

  @Override
  public String fix() {
    return "Open the arena in try-with-resources; if it must outlive the method, store it in a "
        + "field closed by the owner's close(), or use Arena.ofAuto() for GC-managed memory.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public @Nullable Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        TreePath path = getCurrentPath();
        if (Ffm.isArenaFactory(path, ctx, CLOSABLE) && !handled(path, ctx)) {
          ctx.report(node, "This Arena is never closed, so its off-heap memory is never freed. "
              + "Open it in try-with-resources, or close it where its owner is closed.");
        }
        return super.visitMethodInvocation(node, ctx);
      }
    };
  }

  /** Whether the arena created at {@code call} is closed, returned or handed to someone else. */
  private static boolean handled(TreePath call, RuleContext ctx) {
    TreePath child = call;
    TreePath parent = call.getParentPath();
    while (parent.getLeaf() instanceof ParenthesizedTree
        || parent.getLeaf() instanceof TypeCastTree) {
      child = parent;
      parent = parent.getParentPath();
    }
    Tree leaf = parent.getLeaf();
    Tree self = child.getLeaf();
    if (leaf instanceof ReturnTree) {
      return true;
    }
    if (leaf instanceof MethodInvocationTree invocation && invocation.getArguments().contains(self)
        || leaf instanceof NewClassTree created && created.getArguments().contains(self)) {
      return true;
    }
    if (leaf instanceof VariableTree variable) {
      Tree holder = parent.getParentPath().getLeaf();
      String name = variable.getName().toString();
      if (holder instanceof TryTree tryTree && tryTree.getResources().contains(variable)) {
        return true;
      }
      if (holder instanceof ClassTree owner) {
        return closesField(owner, name);
      }
      @Nullable BlockTree body = Ffm.enclosingMethodBody(parent);
      return body != null && localHandled(body, name);
    }
    if (leaf instanceof AssignmentTree assignment) {
      @Nullable Element target =
          ctx.trees().getElement(new TreePath(parent, assignment.getVariable()));
      if (target != null && target.getKind() == ElementKind.FIELD) {
        @Nullable ClassTree owner = enclosingClass(parent);
        return owner != null && closesField(owner, target.getSimpleName().toString());
      }
      @Nullable BlockTree body = Ffm.enclosingMethodBody(parent);
      return target != null && body != null
          && localHandled(body, target.getSimpleName().toString());
    }
    return false;
  }

  private static @Nullable ClassTree enclosingClass(TreePath path) {
    for (TreePath p = path; p != null; p = p.getParentPath()) {
      if (p.getLeaf() instanceof ClassTree owner) {
        return owner;
      }
    }
    return null;
  }

  /** {@code field.close()} or {@code this.field.close()} anywhere in the class. */
  private static boolean closesField(ClassTree owner, String field) {
    return anyMatch(owner, new TreeScanner<Boolean, Void>() {
      @Override
      public Boolean visitMethodInvocation(MethodInvocationTree node, Void unused) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("close")
            && names(select.getExpression(), field)) {
          return Boolean.TRUE;
        }
        return super.visitMethodInvocation(node, unused);
      }

      @Override
      public Boolean reduce(@Nullable Boolean a, @Nullable Boolean b) {
        return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
      }
    });
  }

  /** The local is closed, a try resource, returned, passed on, or stored elsewhere. */
  private static boolean localHandled(BlockTree body, String local) {
    return anyMatch(body, new TreeScanner<Boolean, Void>() {
      @Override
      public Boolean visitMethodInvocation(MethodInvocationTree node, Void unused) {
        boolean closed = node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("close")
            && names(select.getExpression(), local);
        boolean passed = node.getArguments().stream().anyMatch(arg -> names(arg, local));
        return closed || passed || Boolean.TRUE.equals(super.visitMethodInvocation(node, unused));
      }

      @Override
      public Boolean visitNewClass(NewClassTree node, Void unused) {
        return node.getArguments().stream().anyMatch(arg -> names(arg, local))
            || Boolean.TRUE.equals(super.visitNewClass(node, unused));
      }

      @Override
      public Boolean visitReturn(ReturnTree node, Void unused) {
        return node.getExpression() != null && names(node.getExpression(), local);
      }

      @Override
      public Boolean visitTry(TryTree node, Void unused) {
        return node.getResources().stream().anyMatch(resource -> names(resource, local))
            || Boolean.TRUE.equals(super.visitTry(node, unused));
      }

      @Override
      public Boolean visitAssignment(AssignmentTree node, Void unused) {
        return names(node.getExpression(), local)
            || Boolean.TRUE.equals(super.visitAssignment(node, unused));
      }

      @Override
      public Boolean reduce(@Nullable Boolean a, @Nullable Boolean b) {
        return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
      }
    });
  }

  private static boolean anyMatch(Tree root, TreeScanner<Boolean, Void> scanner) {
    return Boolean.TRUE.equals(root.accept(scanner, null));
  }

  /** {@code name} or {@code this.name}. */
  private static boolean names(Tree tree, String name) {
    Tree expression = tree instanceof ExpressionTree e ? Ffm.unwrap(e) : tree;
    return expression instanceof IdentifierTree id && id.getName().contentEquals(name)
        || expression instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals(name)
            && "this".equals(select.getExpression().toString());
  }
}
