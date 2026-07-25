package io.codekoll.rules.security;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CK-HARDCODED-SECRET: a non-placeholder string literal assigned to a variable whose name
 * says it holds a password/key/token. Name-based heuristic with false-friend and
 * template-placeholder exemptions (SPEC §6.5).
 */
public final class HardcodedSecretRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-HARDCODED-SECRET");

  private static final Pattern SECRET_NAME = Pattern.compile(
      "(?i).*(password|passwd|pwd|secret|api[_-]?key|token|credential).*");
  private static final Pattern FALSE_FRIEND = Pattern.compile(
      "(?i).*(prompt|label|field|param|name|header|file|path|env|property|url|endpoint).*");

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
    return "Secret material hardcoded in a string literal";
  }

  @Override
  public String explanation() {
    return "A literal assigned to a password/key/token variable ships the secret to every "
        + "place the code goes: version control history (forever), build artifacts, "
        + "decompiled jars. Rotating it requires a release, and everyone with repo read "
        + "access already has production credentials.";
  }

  @Override
  public String fix() {
    return "Load secrets at runtime from the environment, a vault, or config excluded from "
        + "version control; keep only the lookup key in code.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitVariable(VariableTree node, RuleContext ctx) {
        if (node.getInitializer() != null) {
          check(node, node.getName().toString(), node.getInitializer(), ctx);
        }
        return super.visitVariable(node, ctx);
      }

      @Override
      public Void visitAssignment(AssignmentTree node, RuleContext ctx) {
        check(node, node.getVariable().toString(), node.getExpression(), ctx);
        return super.visitAssignment(node, ctx);
      }

      private void check(com.sun.source.tree.Tree node, String name, ExpressionTree value,
          RuleContext ctx) {
        if (SECRET_NAME.matcher(name).matches()
            && !FALSE_FRIEND.matcher(name).matches()
            && NullFacts.unwrap(value) instanceof LiteralTree literal
            && literal.getValue() instanceof String s
            && !isPlaceholder(s)) {
          ctx.report(node, "'" + name + "' holds a hardcoded value — it is in version "
              + "control forever and in every build artifact. Load it from the "
              + "environment or a vault.");
        }
      }

      private boolean isPlaceholder(String value) {
        String v = value.trim();
        return v.isEmpty()
            || v.startsWith("${") || v.contains("%s") || v.startsWith("{{")
            || v.toLowerCase(Locale.ROOT).startsWith("todo")
            || v.toLowerCase(Locale.ROOT).startsWith("<");
      }
    };
  }
}
