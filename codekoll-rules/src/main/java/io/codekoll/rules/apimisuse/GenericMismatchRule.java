package io.codekoll.rules.apimisuse;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

/**
 * CK-GENERIC-MISMATCH: {@code Map.get/remove/containsKey}, {@code Collection.remove/contains}
 * and {@code List.indexOf} take {@code Object} for backward compatibility — the compiler
 * accepts any argument. When the argument's type is provably unrelated to the collection's
 * key/element type, the call can only ever return null/false: a guaranteed bug that looks
 * like a cache miss.
 *
 * <p>Skips raw receivers, unbounded wildcards, {@code Object} arguments and type variables —
 * only provable mismatches fire (precision policy).
 */
public final class GenericMismatchRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-GENERIC-MISMATCH");

  /** method name → (owner interface, index of the type argument the parameter must match). */
  private record Target(String ownerFqn, int typeArgIndex) {}

  private static final Map<String, Target> MAP_METHODS = Map.of(
      "get", new Target("java.util.Map", 0),
      "remove", new Target("java.util.Map", 0),
      "containsKey", new Target("java.util.Map", 0),
      "containsValue", new Target("java.util.Map", 1));

  private static final Set<String> COLLECTION_METHODS =
      Set.of("remove", "contains", "indexOf", "lastIndexOf");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.API_MISUSE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Argument type can never match the collection's key/element type";
  }

  @Override
  public String explanation() {
    return "Map.get, Collection.remove and friends accept Object for backward "
        + "compatibility, so the compiler cannot catch a wrong-typed argument. "
        + "userCache.get(12345) on a Map<String, User> compiles — and always returns null, "
        + "because an Integer can never equal a String key. The bug presents as an "
        + "eternal cache miss or a remove() that never removes.";
  }

  @Override
  public String fix() {
    return "Pass a value of the collection's key/element type (e.g. the String form of the "
        + "id), or fix the collection's type parameters if they are what's wrong.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getMethodSelect() instanceof MemberSelectTree select) {
          String methodName = select.getIdentifier().toString();
          ExpressionTree receiver = select.getExpression();
          TypeMirror receiverType = ctx.typeOf(new TreePath(getCurrentPath(), receiver));
          TypeMirror argType =
              ctx.typeOf(new TreePath(getCurrentPath(), node.getArguments().get(0)));
          if (receiverType != null && argType != null
              && argType.getKind() != TypeKind.ERROR
              && declaredParameterIsObject(node, ctx)) {
            check(node, methodName, receiverType, argType, ctx);
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      /**
       * The weakly-typed methods all declare their parameter as {@code Object}; an overload
       * with a specific parameter (e.g. {@code List.remove(int)}) was already type-checked
       * by the compiler and must not fire.
       */
      private boolean declaredParameterIsObject(MethodInvocationTree node, RuleContext ctx) {
        return ctx.trees().getElement(getCurrentPath().getParentPath() == null
                ? getCurrentPath()
                : new TreePath(getCurrentPath(), node))
            instanceof javax.lang.model.element.ExecutableElement method
            && method.getParameters().size() == 1
            && "java.lang.Object".equals(
                ctx.types().erasure(method.getParameters().get(0).asType()).toString());
      }

      private void check(MethodInvocationTree node, String methodName, TypeMirror receiverType,
          TypeMirror argType, RuleContext ctx) {
        Target target = MAP_METHODS.get(methodName);
        TypeMirror expected = null;
        String container = "";
        if (target != null && ctx.isSubtypeOf(receiverType, "java.util.Map")) {
          expected = typeArgumentOf(receiverType, target.ownerFqn(), target.typeArgIndex(),
              ctx.types());
          container = target.typeArgIndex() == 0 ? "key" : "value";
        } else if (COLLECTION_METHODS.contains(methodName)
            && ctx.isSubtypeOf(receiverType, "java.util.Collection")) {
          expected = typeArgumentOf(receiverType, "java.util.Collection", 0, ctx.types());
          container = "element";
        }
        if (expected == null) {
          return;
        }
        TypeMirror expectedBoxed = boxed(expected, ctx);
        TypeMirror argBoxed = boxed(argType, ctx);
        if (expectedBoxed == null || argBoxed == null
            || !isProvablyUnrelated(expectedBoxed, argBoxed, ctx)) {
          return;
        }
        String expectedName = simpleName(ctx.qualifiedNameOf(expectedBoxed));
        String argName = simpleName(ctx.qualifiedNameOf(argBoxed));
        String hint = isIntegerVsLong(expectedName, argName)
            ? " (classic int-literal-vs-Long-key bug: suffix the literal with L)"
            : "";
        ctx.report(node, methodName + "() called with " + argName + " but the " + container
            + " type is " + expectedName + " — compiles, but can never match" + hint + ".");
      }

      /** The instantiated type argument of {@code fqn} as implemented by {@code type}. */
      private @Nullable TypeMirror typeArgumentOf(TypeMirror type, String fqn, int index,
          Types types) {
        Deque<TypeMirror> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(type);
        while (!queue.isEmpty()) {
          TypeMirror current = queue.poll();
          if (!(current instanceof DeclaredType declared)) {
            continue;
          }
          String name = types.erasure(declared).toString();
          if (!seen.add(name)) {
            continue;
          }
          if (name.equals(fqn)) {
            var args = declared.getTypeArguments();
            if (args.size() <= index) {
              return null;  // raw type
            }
            TypeMirror arg = args.get(index);
            // Only concrete declared types are provable; wildcards/vars are skipped.
            return arg instanceof DeclaredType ? arg : null;
          }
          queue.addAll(types.directSupertypes(declared));
        }
        return null;
      }

      private boolean isProvablyUnrelated(TypeMirror a, TypeMirror b, RuleContext ctx) {
        Types types = ctx.types();
        return !types.isAssignable(types.erasure(a), types.erasure(b))
            && !types.isAssignable(types.erasure(b), types.erasure(a));
      }

      private @Nullable TypeMirror boxed(TypeMirror type, RuleContext ctx) {
        if (type.getKind().isPrimitive()) {
          return ctx.types().boxedClass(
              (javax.lang.model.type.PrimitiveType) type).asType();
        }
        return type instanceof DeclaredType ? type : null;
      }

      private String simpleName(String qualified) {
        return qualified.isEmpty() ? "?" : qualified.replaceFirst(".*\\.", "");
      }

      private boolean isIntegerVsLong(String expected, String arg) {
        return ("Long".equals(expected) && "Integer".equals(arg))
            || ("Integer".equals(expected) && "Long".equals(arg));
      }
    };
  }
}
