package io.codekoll.rules.performance;

import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * CK-REGEX-IN-LOOP: {@code Pattern.compile(<constant>)} or regex-taking String methods with
 * a constant pattern inside a loop — the regex recompiles every iteration. Single non-meta
 * character split is exempt (the JDK fast-paths it).
 */
public final class RegexInLoopRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-REGEX-IN-LOOP");

  private static final Set<String> STRING_REGEX_METHODS =
      Set.of("matches", "replaceAll", "replaceFirst", "split");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.PERFORMANCE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Constant regex compiled inside a loop";
  }

  @Override
  public String explanation() {
    return "Pattern.compile parses and builds an NFA every call. Inside a loop over N "
        + "elements the SAME constant pattern is recompiled N times — pure CPU waste that "
        + "profilers routinely find dominating hot paths (String.matches/replaceAll/split "
        + "compile internally too).";
  }

  @Override
  public String fix() {
    return "Hoist it: private static final Pattern P = Pattern.compile(\"...\"); and use "
        + "P.matcher(s) / P.split(s) inside the loop.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && insideLoop()
            && constantRegexArg(node) != null) {
          String name = select.getIdentifier().toString();
          if (isPatternCompile(select, name, ctx)) {
            ctx.report(node, "Pattern.compile of a constant inside a loop recompiles every "
                + "iteration. Hoist to a static final Pattern.");
          } else if (STRING_REGEX_METHODS.contains(name)
              && isStringReceiver(select, ctx)
              && !isFastPathSplit(name, constantRegexArg(node))) {
            ctx.report(node, "String." + name + " compiles its regex on every call — in a "
                + "loop that is once per iteration. Hoist a static final Pattern.");
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isPatternCompile(MemberSelectTree select, String name, RuleContext ctx) {
        if (!"compile".equals(name)) {
          return false;
        }
        Element element = ctx.trees().getElement(
            new TreePath(getCurrentPath(), select.getExpression()));
        return element instanceof TypeElement type
            && "java.util.regex.Pattern".equals(type.getQualifiedName().toString());
      }

      private boolean isStringReceiver(MemberSelectTree select, RuleContext ctx) {
        return "java.lang.String".equals(ctx.qualifiedNameOf(
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()))));
      }

      private String constantRegexArg(MethodInvocationTree node) {
        if (!node.getArguments().isEmpty()
            && node.getArguments().get(0) instanceof LiteralTree literal
            && literal.getValue() instanceof String s) {
          return s;
        }
        return null;
      }

      /** Single-char non-meta split is fast-pathed by the JDK; flagging it is noise. */
      private boolean isFastPathSplit(String method, String regex) {
        return "split".equals(method) && regex != null && regex.length() == 1
            && ".$|()[{^?*+\\".indexOf(regex.charAt(0)) < 0;
      }

      private boolean insideLoop() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          Tree leaf = p.getLeaf();
          if (leaf instanceof LambdaExpressionTree
              || leaf instanceof com.sun.source.tree.MethodTree) {
            return false;
          }
          if (leaf instanceof ForLoopTree || leaf instanceof EnhancedForLoopTree
              || leaf instanceof WhileLoopTree || leaf instanceof DoWhileLoopTree) {
            return true;
          }
        }
        return false;
      }
    };
  }
}
