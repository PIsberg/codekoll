package io.codekoll.rules.correctness;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-DEFAULT-CHARSET: byte/char conversions without an explicit charset — behavior depends
 * on the platform default; data written on one machine is mojibake on another.
 */
public final class DefaultCharsetRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-DEFAULT-CHARSET");

  private static final Set<String> CHARSET_CTOR_TYPES = Set.of(
      "java.io.FileReader", "java.io.FileWriter",
      "java.io.InputStreamReader", "java.io.OutputStreamWriter");

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
    return "Byte/char conversion relying on the platform default charset";
  }

  @Override
  public String explanation() {
    return "getBytes(), new String(bytes) and the classic Reader/Writer constructors use "
        + "the JVM's platform default charset — different per OS, locale and container "
        + "image. A file written as UTF-8 on the build server reads as mojibake on a "
        + "Windows-1252 desktop: same code, different data.";
  }

  @Override
  public String fix() {
    return "Pass the charset explicitly: getBytes(StandardCharsets.UTF_8), "
        + "new String(bytes, UTF_8), new InputStreamReader(in, UTF_8).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().isEmpty()
            && node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("getBytes")
            && isString(select.getExpression(), ctx)) {
          ctx.report(node, "getBytes() uses the platform default charset — encoding "
              + "changes per machine. Pass StandardCharsets.UTF_8.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      @Override
      public Void visitNewClass(NewClassTree node, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), node));
        String name = ctx.qualifiedNameOf(type);
        if (CHARSET_CTOR_TYPES.contains(name) && node.getArguments().size() == 1) {
          ctx.report(node, "new " + name.replaceFirst(".*\\.", "") + " without a charset "
              + "uses the platform default. Pass StandardCharsets.UTF_8 "
              + "(or use Files.newBufferedReader).");
        } else if ("java.lang.String".equals(name)
            && node.getArguments().size() == 1
            && isByteArray(node.getArguments().get(0), ctx)) {
          ctx.report(node, "new String(byte[]) decodes with the platform default charset. "
              + "Pass StandardCharsets.UTF_8.");
        }
        return super.visitNewClass(node, ctx);
      }

      private boolean isString(ExpressionTree receiver, RuleContext ctx) {
        return "java.lang.String".equals(ctx.qualifiedNameOf(
            ctx.typeOf(new TreePath(getCurrentPath(), receiver))));
      }

      private boolean isByteArray(ExpressionTree arg, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), arg));
        return type != null && "byte[]".equals(type.toString());
      }
    };
  }
}
