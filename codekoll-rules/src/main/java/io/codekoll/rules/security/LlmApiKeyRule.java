package io.codekoll.rules.security;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * CK-LLM-API-KEY: a string literal shaped like an OpenAI, Anthropic or Google AI key, whatever
 * the variable holding it is called.
 */
public final class LlmApiKeyRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-LLM-API-KEY");

  /** OpenAI ({@code sk-}, {@code sk-proj-}), Anthropic ({@code sk-ant-}), Google ({@code AIza}). */
  private static final Pattern KEY = Pattern.compile(
      "sk-(?:ant-|proj-)?[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{35}");

  private static final List<String> PLACEHOLDERS =
      List.of("xxxx", "your", "example", "placeholder", "...", "changeme");

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
    return "Hardcoded LLM provider API key";
  }

  @Override
  public String explanation() {
    return "A literal shaped like an OpenAI, Anthropic or Google AI key is in source control for "
        + "good and in every build artifact. Anyone who reads the repository or decompiles the "
        + "jar can run up usage on the account and read what it has stored; providers scan "
        + "public repositories and revoke leaked keys, which takes the feature down with them.";
  }

  @Override
  public String fix() {
    return "Load the key from the environment or a secrets manager "
        + "(System.getenv(\"OPENAI_API_KEY\")) and rotate the leaked one: it stays in the "
        + "history even after this line is removed.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public @Nullable Void visitLiteral(LiteralTree node, RuleContext ctx) {
        if (node.getValue() instanceof String value && looksLikeKey(value)
            && !reportedByHardcodedSecret(getCurrentPath())) {
          ctx.report(node, "This literal looks like an LLM provider API key: it is in version "
              + "control and every artifact. Load it from the environment, and rotate this one.");
        }
        return super.visitLiteral(node, ctx);
      }
    };
  }

  private static boolean looksLikeKey(String value) {
    if (!KEY.matcher(value.strip()).matches()) {
      return false;
    }
    String lower = value.toLowerCase(Locale.ROOT);
    return PLACEHOLDERS.stream().noneMatch(lower::contains);
  }

  /** The literal initializes or is assigned to a variable CK-HARDCODED-SECRET reports by name. */
  private static boolean reportedByHardcodedSecret(TreePath literal) {
    Tree parent = literal.getParentPath().getLeaf();
    String name = parent instanceof VariableTree variable ? variable.getName().toString()
        : parent instanceof AssignmentTree assignment ? assignment.getVariable().toString()
        : "";
    return !name.isEmpty() && HardcodedSecretRule.namesSecret(name);
  }
}
