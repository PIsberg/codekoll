package io.codekoll.rules.security;

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
import java.util.regex.Pattern;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * CK-REDOS: a regex literal with a nested-quantifier shape ({@code (a+)+}, {@code (a*)*},
 * {@code (a|aa)+}) — catastrophic backtracking, exploitable as denial of service.
 */
public final class RedosRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-REDOS");

  private static final Set<String> STRING_REGEX_METHODS =
      Set.of("matches", "replaceAll", "replaceFirst", "split");

  // A quantified group whose body itself ends in a quantifier: (…+)+ / (…*)* / (…+)* etc.
  private static final Pattern NESTED_QUANTIFIER =
      Pattern.compile("\\([^()]*[+*][^()]*\\)[+*]");
  // Quantified group containing an alternation of overlapping tokens: (a|aa)+, (\\w+|\\d)+
  private static final Pattern QUANTIFIED_ALTERNATION =
      Pattern.compile("\\([^()]*\\|[^()]*\\)[+*]");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.SECURITY;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Regex with catastrophic-backtracking shape (ReDoS)";
  }

  @Override
  public String explanation() {
    return "A quantifier applied to a group that is itself quantified — (a+)+ — makes the "
        + "engine try exponentially many ways to match a failing input. A crafted string of "
        + "a few dozen characters can pin a CPU core for minutes: a single request becomes "
        + "a denial-of-service (ReDoS).";
  }

  @Override
  public String fix() {
    return "Rewrite without nested quantifiers, use a possessive quantifier (a++), or an "
        + "atomic group (?>a+). Validate lengths before matching untrusted input.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && !node.getArguments().isEmpty()) {
          String regex = literalRegexArg(node, select, ctx);
          if (regex != null && isVulnerable(regex)) {
            ctx.report(node, "Regex \"" + regex + "\" has a nested-quantifier shape — "
                + "crafted input causes catastrophic backtracking (ReDoS). Use possessive "
                + "quantifiers or rewrite.");
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private String literalRegexArg(MethodInvocationTree node, MemberSelectTree select,
          RuleContext ctx) {
        String name = select.getIdentifier().toString();
        if ("compile".equals(name) && isPattern(select, ctx)) {
          return constant(node.getArguments().get(0));
        }
        if (STRING_REGEX_METHODS.contains(name)) {
          return constant(node.getArguments().get(0));
        }
        return null;
      }

      private boolean isPattern(MemberSelectTree select, RuleContext ctx) {
        Element element = ctx.trees().getElement(
            new TreePath(getCurrentPath(), select.getExpression()));
        return element instanceof TypeElement type
            && "java.util.regex.Pattern".equals(type.getQualifiedName().toString());
      }

      private String constant(com.sun.source.tree.ExpressionTree arg) {
        return arg instanceof LiteralTree literal && literal.getValue() instanceof String s
            ? s : null;
      }

      private boolean isVulnerable(String regex) {
        return NESTED_QUANTIFIER.matcher(regex).find()
            || QUANTIFIED_ALTERNATION.matcher(regex).find();
      }
    };
  }
}
