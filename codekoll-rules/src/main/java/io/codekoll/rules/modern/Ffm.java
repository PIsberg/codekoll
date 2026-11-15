package io.codekoll.rules.modern;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.jspecify.annotations.Nullable;

/** Shared shape checks for the FFM ({@code java.lang.foreign}) lifecycle rules. */
final class Ffm {

  static final String ARENA = "java.lang.foreign.Arena";
  static final String SEGMENT = "java.lang.foreign.MemorySegment";

  private Ffm() {}

  /** Whether the call at {@code call} is one of the named static factories on {@code Arena}. */
  static boolean isArenaFactory(TreePath call, RuleContext ctx, Set<String> names) {
    return isMethodOf(call, ctx, ARENA, names);
  }

  /** Whether the call at {@code call} resolves to one of {@code names} declared by {@code owner}. */
  static boolean isMethodOf(TreePath call, RuleContext ctx, String owner, Set<String> names) {
    @Nullable Element element = ctx.trees().getElement(call);
    return element instanceof ExecutableElement method
        && names.contains(method.getSimpleName().toString())
        && method.getEnclosingElement() instanceof TypeElement type
        && type.getQualifiedName().contentEquals(owner);
  }

  /** Strips parentheses and casts. */
  static ExpressionTree unwrap(ExpressionTree expression) {
    ExpressionTree current = expression;
    while (true) {
      if (current instanceof ParenthesizedTree paren) {
        current = paren.getExpression();
      } else if (current instanceof TypeCastTree cast) {
        current = cast.getExpression();
      } else {
        return current;
      }
    }
  }

  /** The body of the method enclosing {@code path}, or null outside a method. */
  static @Nullable BlockTree enclosingMethodBody(TreePath path) {
    for (TreePath p = path; p != null; p = p.getParentPath()) {
      if (p.getLeaf() instanceof MethodTree method) {
        return method.getBody();
      }
      if (p.getLeaf() instanceof ClassTree) {
        return null;
      }
    }
    return null;
  }

  /** The initializer of the local named {@code name} declared in {@code body}, if any. */
  static @Nullable ExpressionTree localInitializer(BlockTree body, String name) {
    ExpressionTree[] found = new ExpressionTree[1];
    body.accept(new TreeScanner<Void, Void>() {
      @Override
      public @Nullable Void visitVariable(VariableTree variable, Void unused) {
        if (found[0] == null && variable.getName().contentEquals(name)) {
          found[0] = variable.getInitializer();
        }
        return super.visitVariable(variable, unused);
      }
    }, null);
    return found[0];
  }
}
