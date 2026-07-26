package io.codekoll.rules.apimisuse;

import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;

/**
 * CK-PRIMITIVE-ARRAY-VARARGS: a primitive array handed to a generic varargs factory
 * ({@code Arrays.asList}, {@code Stream.of}). Generics cannot hold {@code int}, so the array
 * is wrapped as a <em>single</em> element instead of being spread.
 */
public final class PrimitiveArrayVarargsRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-PRIMITIVE-ARRAY-VARARGS");

  /** Declaring type → the {@code type.method} spelling used in the message. */
  private static final Map<String, String> FACTORIES = Map.of(
      "java.util.Arrays", "asList",
      "java.util.stream.Stream", "of");

  /** Declaring type → the primitive-aware replacement. */
  private static final Map<String, String> REPLACEMENT = Map.of(
      "java.util.Arrays", "Arrays.stream(array).boxed().toList()",
      "java.util.stream.Stream", "Arrays.stream(array) (an IntStream/LongStream/DoubleStream)");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Primitive array passed to Arrays.asList/Stream.of — wrapped as one element, "
        + "not spread";
  }

  @Override
  public String explanation() {
    return "Varargs spreading needs a reference array: generics have no int, so int[] cannot "
        + "become T... . Arrays.asList(new int[]{1, 2, 3}) therefore infers T = int[] and "
        + "returns a List<int[]> of size ONE, holding the whole array. It compiles without a "
        + "warning, and the failure is silent rather than loud: size() is 1, iteration yields "
        + "the array object, contains(1) is false, and toString prints [I@1a2b3c. The very "
        + "same code with Integer[] behaves as intended, which is why it survives review.";
  }

  @Override
  public String fix() {
    return "Box the elements: Arrays.stream(array).boxed().toList(). For a stream use "
        + "Arrays.stream(array), which gives a primitive IntStream/LongStream/DoubleStream. "
        + "Or declare the array as Integer[]/Long[]/Double[] in the first place.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        check(node, ctx);
        return super.visitMethodInvocation(node, ctx);
      }

      private void check(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() != 1
            || !(node.getMethodSelect() instanceof MemberSelectTree select)) {
          return;
        }
        String owner = receiverType(select, ctx);
        String expected = owner == null ? null : FACTORIES.get(owner);
        if (expected == null || !expected.equals(select.getIdentifier().toString())) {
          return;
        }
        ExpressionTree arg = node.getArguments().get(0);
        String component = primitiveComponent(arg, ctx);
        if (component == null || wantsTheArrayItself(ctx)) {
          return;
        }
        String simple = owner.substring(owner.lastIndexOf('.') + 1);
        ctx.report(node, simple + "." + expected + " cannot spread a " + component
            + "[] — generics have no " + component + ", so this holds the array as a single "
            + "element and has size 1. Use " + REPLACEMENT.get(owner) + ".");
      }

      /** Primitive component type name when {@code arg} is a primitive array, else null. */
      private String primitiveComponent(ExpressionTree arg, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), arg));
        if (!(type instanceof ArrayType array)) {
          return null;
        }
        TypeMirror component = array.getComponentType();
        return component.getKind().isPrimitive() ? component.toString() : null;
      }

      /**
       * True when the surrounding declaration asks for a collection OF arrays —
       * {@code List<int[]> rows = Arrays.asList(row);} is deliberate, not a spread that failed.
       */
      private boolean wantsTheArrayItself(RuleContext ctx) {
        Tree parent = getCurrentPath().getParentPath() == null
            ? null : getCurrentPath().getParentPath().getLeaf();
        return parent instanceof VariableTree variable
            && isWrittenInSource(variable.getType(), ctx)
            && variable.getType() instanceof ParameterizedTypeTree parameterized
            && parameterized.getTypeArguments().stream()
                .anyMatch(ArrayTypeTree.class::isInstance);
      }

      /**
       * True when the type was spelled out by the author. A {@code var} declaration carries the
       * inferred type as a synthesized tree, which has no end position in the source — and an
       * inferred {@code List<int[]>} is the bug, not a statement of intent.
       */
      private boolean isWrittenInSource(Tree type, RuleContext ctx) {
        return ctx.trees().getSourcePositions().getEndPosition(ctx.unit(), type) >= 0;
      }

      private String receiverType(MemberSelectTree select, RuleContext ctx) {
        Element element =
            ctx.trees().getElement(new TreePath(getCurrentPath(), select.getExpression()));
        return element instanceof TypeElement type ? type.getQualifiedName().toString() : null;
      }
    };
  }
}
