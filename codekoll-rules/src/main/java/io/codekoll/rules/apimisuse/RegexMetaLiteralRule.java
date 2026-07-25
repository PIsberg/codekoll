package io.codekoll.rules.apimisuse;

import com.sun.source.tree.LiteralTree;
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
import javax.lang.model.type.TypeMirror;

/**
 * CK-REGEX-META-LITERAL: {@code split}/{@code replaceAll}/{@code matches} with a literal
 * that is a single bare regex metacharacter — {@code split(".")} matches everything
 * (empty result), {@code split("(")} throws PatternSyntaxException.
 */
public final class RegexMetaLiteralRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-REGEX-META-LITERAL");

  private static final Set<String> REGEX_METHODS =
      Set.of("split", "replaceAll", "replaceFirst", "matches");
  private static final String META = ".$|^*+?()[{\\";

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
    return "split/replaceAll with a bare regex metacharacter literal";
  }

  @Override
  public String explanation() {
    return "These String methods take a REGEX, not a plain separator. \"file.txt\".split"
        + "(\".\") splits on 'any character' — the result is an empty array, and the "
        + "filename parsing silently produces nothing. \"(\" or \"[\" don't even parse: "
        + "PatternSyntaxException at runtime.";
  }

  @Override
  public String fix() {
    return "Escape it — split(\"\\\\.\") — or use Pattern.quote(\".\"); for replacing, "
        + "String.replace(\".\", \"-\") is literal and simpler.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (!node.getArguments().isEmpty()
            && node.getMethodSelect() instanceof MemberSelectTree select
            && REGEX_METHODS.contains(select.getIdentifier().toString())
            && node.getArguments().get(0) instanceof LiteralTree literal
            && literal.getValue() instanceof String regex
            && regex.length() == 1
            && META.indexOf(regex.charAt(0)) >= 0
            && isStringReceiver(select, ctx)) {
          ctx.report(node, "\"" + regex + "\" is a regex metacharacter: "
              + ("(".equals(regex) || "[".equals(regex) || "{".equals(regex)
                  || "\\".equals(regex)
                  ? "PatternSyntaxException at runtime."
                  : "it matches something else entirely (split(\".\") returns an empty "
                      + "array).")
              + " Escape it: \"\\\\" + regex + "\".");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isStringReceiver(MemberSelectTree select, RuleContext ctx) {
        TypeMirror receiver =
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        return "java.lang.String".equals(ctx.qualifiedNameOf(receiver));
      }
    };
  }
}
