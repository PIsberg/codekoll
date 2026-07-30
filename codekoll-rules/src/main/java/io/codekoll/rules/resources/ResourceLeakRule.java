package io.codekoll.rules.resources;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
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
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-RESOURCE-LEAK: an {@link AutoCloseable} is created but never closed on all paths.
 *
 * <p>v1 heuristic (SPEC §5.7): a {@code new <AutoCloseable>} is fine when it is a
 * try-with-resources resource, is returned, is consumed as an argument (decorator/ownership
 * transfer), is assigned to a field (owner's responsibility), or — when assigned to a local —
 * any {@code local.close()} call exists in the same method. No-op closeables
 * (ByteArrayInputStream & co) are never flagged.
 */
public final class ResourceLeakRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-RESOURCE-LEAK");

  /**
   * Disposal is not spelled {@code close()} everywhere. An ExecutorService released with
   * {@code shutdown()} in a finally block is the pre-Java-19 idiom and is still the common one;
   * flagging it as a leak is wrong.
   */
  private static final Set<String> DISPOSAL_METHODS =
      Set.of("close", "shutdown", "shutdownNow", "dispose", "release");

  /** Closing these is a no-op; flagging them is pure noise. */
  private static final Set<String> NO_OP_CLOSEABLES = Set.of(
      "java.io.ByteArrayInputStream", "java.io.ByteArrayOutputStream",
      "java.io.StringReader", "java.io.StringWriter",
      "java.io.CharArrayReader", "java.io.CharArrayWriter");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "AutoCloseable created but never closed";
  }

  @Override
  public String explanation() {
    return "The created stream/connection/socket holds an operating-system resource (file "
        + "handle, socket, connection-pool slot) that garbage collection does not promptly "
        + "release. Under load the process runs out of handles or connections — the classic "
        + "'too many open files' production outage, hours after the leak started.";
  }

  @Override
  public String fix() {
    return "Use try-with-resources: try (var stream = new FileInputStream(path)) { ... } — "
        + "it closes on every path, including exceptions.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitNewClass(NewClassTree node, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), node));
        if (ctx.isSubtypeOf(type, "java.lang.AutoCloseable")
            && !NO_OP_CLOSEABLES.contains(ctx.qualifiedNameOf(type))
            && !isHandled(ctx)) {
          ctx.report(node, ctx.qualifiedNameOf(type).replaceFirst(".*\\.", "")
              + " is never closed. Use try-with-resources: "
              + "try (var r = ...) { ... }");
        }
        return super.visitNewClass(node, ctx);
      }

      /** True when the creation is closed, consumed, or ownership-transferred. */
      private boolean isHandled(RuleContext ctx) {
        TreePath path = getCurrentPath();
        TreePath child = path;
        for (TreePath parent = path.getParentPath(); parent != null;
            child = parent, parent = parent.getParentPath()) {
          Tree leaf = parent.getLeaf();
          // Consumed as an argument of a wrapping call/constructor → ownership transferred.
          if (leaf instanceof MethodInvocationTree call
              && call.getArguments().contains(child.getLeaf())) {
            return true;
          }
          if (leaf instanceof NewClassTree outer
              && outer.getArguments().contains(child.getLeaf())) {
            return true;
          }
          if (leaf instanceof ReturnTree) {
            return true;
          }
          if (leaf instanceof VariableTree variable) {
            return isTryResource(parent) || isClosedLocal(parent, variable, ctx);
          }
          if (leaf instanceof AssignmentTree assignment) {
            return isFieldOrClosed(parent, assignment, ctx);
          }
          if (leaf instanceof MethodTree
              || leaf instanceof com.sun.source.tree.LambdaExpressionTree) {
            break;
          }
        }
        return false;
      }

      private boolean isTryResource(TreePath variablePath) {
        Tree parent = variablePath.getParentPath().getLeaf();
        return parent instanceof TryTree tryTree
            && tryTree.getResources().contains(variablePath.getLeaf());
      }

      private boolean isClosedLocal(TreePath variablePath, VariableTree variable,
          RuleContext ctx) {
        Element symbol = ctx.trees().getElement(variablePath);
        if (symbol != null && symbol.getKind() == ElementKind.FIELD) {
          return true;  // field assignment: the owner closes it elsewhere
        }
        String name = variable.getName().toString();
        return methodContainsClose(variablePath, name)
            || methodReleasesOwnership(variablePath, name);
      }

      /**
       * A local that is returned, or handed to another call, has left this method's ownership:
       * {@code var b = new Bridge(...); registry.start(b); return b;} is a factory, not a leak,
       * and the caller is the one that must close it. This mirrors the ownership transfer the
       * rule already recognises when the creation expression itself is returned or passed on.
       */
      private boolean methodReleasesOwnership(TreePath variablePath, String name) {
        Tree body = enclosingMethodBody(variablePath);
        if (body == null) {
          return false;
        }
        return Boolean.TRUE.equals(new TreeScanner<Boolean, Void>() {
          @Override
          public Boolean visitReturn(ReturnTree node, Void unused) {
            return isName(node.getExpression()) ? Boolean.TRUE : super.visitReturn(node, unused);
          }

          @Override
          public Boolean visitMethodInvocation(MethodInvocationTree node, Void unused) {
            return node.getArguments().stream().anyMatch(this::isName)
                ? Boolean.TRUE : super.visitMethodInvocation(node, unused);
          }

          @Override
          public Boolean reduce(@Nullable Boolean first, @Nullable Boolean second) {
            return Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second);
          }

          private boolean isName(@Nullable Tree expr) {
            return expr instanceof IdentifierTree id && id.getName().contentEquals(name);
          }
        }.scan(body, null));
      }

      private @Nullable Tree enclosingMethodBody(TreePath path) {
        for (TreePath p = path; p != null; p = p.getParentPath()) {
          if (p.getLeaf() instanceof MethodTree method) {
            return method.getBody();
          }
        }
        return null;
      }

      private boolean isFieldOrClosed(TreePath assignmentPath, AssignmentTree assignment,
          RuleContext ctx) {
        ExpressionTree target = assignment.getVariable();
        if (target instanceof MemberSelectTree) {
          return true;  // this.f = … / obj.f = … → owner's responsibility
        }
        Element symbol = ctx.trees().getElement(
            new TreePath(assignmentPath, target));
        if (symbol != null && symbol.getKind() == ElementKind.FIELD) {
          return true;
        }
        return methodContainsClose(assignmentPath, target.toString());
      }

      /** Any reachable {@code name.close()} in the enclosing method suppresses (v1). */
      private boolean methodContainsClose(TreePath from, String name) {
        MethodTree method = enclosingMethod(from);
        if (method == null || method.getBody() == null) {
          return false;
        }
        Boolean found = method.getBody().accept(new TreeScanner<Boolean, Void>() {
          @Override
          public Boolean visitMethodInvocation(MethodInvocationTree call, Void unused) {
            if (call.getMethodSelect() instanceof MemberSelectTree select
                && DISPOSAL_METHODS.contains(select.getIdentifier().toString())
                && select.getExpression().toString().equals(name)) {
              return true;
            }
            return super.visitMethodInvocation(call, unused);
          }

          @Override
          public Boolean reduce(@Nullable Boolean a, @Nullable Boolean b) {
            return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
          }
        }, null);
        return Boolean.TRUE.equals(found);
      }

      private @Nullable MethodTree enclosingMethod(TreePath from) {
        for (TreePath p = from; p != null; p = p.getParentPath()) {
          if (p.getLeaf() instanceof MethodTree method) {
            return method;
          }
        }
        return null;
      }
    };
  }

}
