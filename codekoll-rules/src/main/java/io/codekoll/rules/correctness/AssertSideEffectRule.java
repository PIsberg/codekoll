package io.codekoll.rules.correctness;

import com.sun.source.tree.AssertTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.List;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-ASSERT-SIDE-EFFECT: state changed inside an {@code assert} expression. Assertions are
 * disabled unless the JVM is started with {@code -ea}, so the mutation silently never happens
 * in production while it does happen under test.
 */
public final class AssertSideEffectRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-ASSERT-SIDE-EFFECT");

  /** Methods that mutate their receiver — only consulted when the receiver type matches below. */
  private static final Set<String> MUTATOR_METHODS = Set.of(
      "add", "addAll", "addFirst", "addLast", "append", "clear", "compute", "computeIfAbsent",
      "computeIfPresent", "delete", "insert", "merge", "next", "offer", "poll", "pop", "push",
      "put", "putAll", "putIfAbsent", "remove", "removeAll", "removeIf", "retainAll", "set",
      "setLength", "take", "addAndGet", "compareAndSet", "decrementAndGet", "getAndAdd",
      "getAndDecrement", "getAndIncrement", "getAndSet", "incrementAndGet");

  /** Receiver types for which the names above are known to be mutating, not query, methods. */
  private static final List<String> MUTABLE_RECEIVERS = List.of(
      "java.util.Collection",
      "java.util.Map",
      "java.util.Iterator",
      "java.lang.StringBuilder",
      "java.lang.StringBuffer",
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
    return RulePack.CORRECTNESS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "State changed inside an assert — skipped entirely when assertions are disabled";
  }

  @Override
  public String explanation() {
    return "Assertions are off by default: unless the JVM is started with -ea the whole assert "
        + "expression is never evaluated. Any state change written inside it — an assignment, "
        + "an increment, or a mutating call like list.add(...) — simply does not happen in "
        + "production, while test runs (which usually do enable -ea) perform it. The code then "
        + "passes every test and behaves differently where it matters, with nothing thrown and "
        + "nothing logged to point at the assert.";
  }

  @Override
  public String fix() {
    return "Move the state change out of the assert and assert on its result instead: "
        + "boolean added = seen.add(id); assert added;";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitAssignment(com.sun.source.tree.AssignmentTree node, RuleContext ctx) {
        reportIfInsideAssert(node, "an assignment", ctx);
        return super.visitAssignment(node, ctx);
      }

      @Override
      public Void visitCompoundAssignment(CompoundAssignmentTree node, RuleContext ctx) {
        reportIfInsideAssert(node, "a compound assignment", ctx);
        return super.visitCompoundAssignment(node, ctx);
      }

      @Override
      public Void visitUnary(UnaryTree node, RuleContext ctx) {
        if (isIncrementOrDecrement(node.getKind())) {
          reportIfInsideAssert(node, "an increment/decrement", ctx);
        }
        return super.visitUnary(node, ctx);
      }

      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (isKnownMutator(node, ctx)) {
          reportIfInsideAssert(node, "a mutating call", ctx);
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isIncrementOrDecrement(Tree.Kind kind) {
        return kind == Tree.Kind.PREFIX_INCREMENT || kind == Tree.Kind.POSTFIX_INCREMENT
            || kind == Tree.Kind.PREFIX_DECREMENT || kind == Tree.Kind.POSTFIX_DECREMENT;
      }

      private boolean isKnownMutator(MethodInvocationTree node, RuleContext ctx) {
        // An unqualified call has no visible receiver: too little information, stay silent.
        if (!(node.getMethodSelect() instanceof MemberSelectTree select)
            || !MUTATOR_METHODS.contains(select.getIdentifier().toString())) {
          return false;
        }
        TypeMirror receiver =
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        return MUTABLE_RECEIVERS.stream().anyMatch(fqn -> ctx.isSubtypeOf(receiver, fqn));
      }

      private void reportIfInsideAssert(Tree node, String what, RuleContext ctx) {
        if (enclosingAssert(getCurrentPath()) == null) {
          return;
        }
        ctx.report(node, "This assert performs " + what + " — assertions are disabled unless "
            + "the JVM runs with -ea, so in production this state change never happens. Move it "
            + "out of the assert and assert on the result.");
      }

      /** Nearest enclosing assert, not crossing a method or class boundary. */
      private AssertTree enclosingAssert(TreePath start) {
        for (TreePath p = start; p != null; p = p.getParentPath()) {
          Tree tree = p.getLeaf();
          if (tree instanceof AssertTree assertTree) {
            return assertTree;
          }
          if (tree instanceof MethodTree || tree instanceof ClassTree) {
            return null;
          }
        }
        return null;
      }
    };
  }
}
