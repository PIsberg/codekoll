package io.codekoll.rules.security;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * CK-LLM-UNSAFE-SINK: a model's completion handed straight to a script engine, a shell, SQL or
 * SpEL, which turns prompt injection into code execution.
 */
public final class LlmUnsafeSinkRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-LLM-UNSAFE-SINK");

  private static final List<String> MODEL_TYPES = List.of("ChatClient", "ChatModel",
      "ChatLanguageModel", "LanguageModel", "OpenAIClient", "AnthropicClient", "ChatResponse",
      "AiMessage", "ChatCompletion");

  /** Receiver type name (suffix) to the methods whose first argument is interpreted. */
  private static final Map<String, Set<String>> SINKS = Map.of(
      "ScriptEngine", Set.of("eval"),
      "Runtime", Set.of("exec"),
      "ProcessBuilder", Set.of("command"),
      "Statement", Set.of("execute", "executeQuery", "executeUpdate", "executeLargeUpdate",
          "addBatch"),
      "Connection", Set.of("prepareStatement", "prepareCall"),
      "JdbcTemplate", Set.of("query", "queryForList", "queryForObject", "queryForMap",
          "queryForRowSet", "update", "execute", "batchUpdate"),
      "ExpressionParser", Set.of("parseExpression"));

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
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "LLM output executed as code, a command, SQL or SpEL";
  }

  @Override
  public String explanation() {
    return "A model's reply is shaped by everything in its prompt, including any untrusted text "
        + "it was given to read: a document, an e-mail, a web page. Passing the reply straight "
        + "to ScriptEngine.eval, a shell, a SQL statement or a SpEL parser lets whoever wrote "
        + "that text choose what runs, with the application's privileges. It works perfectly in "
        + "every demo, because the demo prompt is friendly.";
  }

  @Override
  public String fix() {
    return "Never execute model output directly. Validate it against an allow-list, or let the "
        + "model choose among fixed operations with typed parameters (tool calls, bound SQL "
        + "parameters) instead of producing code.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public @Nullable Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (!node.getArguments().isEmpty()
            && node.getMethodSelect() instanceof MemberSelectTree select
            && isSink(select, ctx)) {
          checkArguments(node.getArguments().subList(0, 1), ctx);
        }
        return super.visitMethodInvocation(node, ctx);
      }

      @Override
      public @Nullable Void visitNewClass(NewClassTree node, RuleContext ctx) {
        if ("java.lang.ProcessBuilder".equals(ctx.qualifiedNameOf(ctx.typeOf(getCurrentPath())))) {
          checkArguments(node.getArguments(), ctx);
        }
        return super.visitNewClass(node, ctx);
      }

      private boolean isSink(MemberSelectTree select, RuleContext ctx) {
        String receiver =
            ctx.qualifiedNameOf(ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression())));
        String simple = receiver.substring(receiver.lastIndexOf('.') + 1);
        return SINKS.entrySet().stream().anyMatch(sink -> simple.endsWith(sink.getKey())
            && sink.getValue().contains(select.getIdentifier().toString()));
      }

      private void checkArguments(List<? extends ExpressionTree> args, RuleContext ctx) {
        for (ExpressionTree arg : args) {
          if (isModelOutput(arg, getCurrentPath(), ctx, true)) {
            ctx.report(arg, "This value is a language model's reply, executed without "
                + "validation: whoever controls text in the prompt controls what runs. Validate "
                + "it against an allow-list, or use a fixed operation with typed parameters.");
          }
        }
      }
    };
  }

  /**
   * A call chain with a receiver typed as a model client or reply, or (one hop, when
   * {@code followLocal}) a local initialized from one.
   */
  private static boolean isModelOutput(ExpressionTree expression, TreePath at, RuleContext ctx,
      boolean followLocal) {
    ExpressionTree current = NullFacts.unwrap(expression);
    if (followLocal && current instanceof IdentifierTree id) {
      @Nullable ExpressionTree init = localInitializer(at, id.getName().toString());
      return init != null && isModelOutput(init, at, ctx, false);
    }
    while (current instanceof MethodInvocationTree call
        && call.getMethodSelect() instanceof MemberSelectTree select) {
      ExpressionTree receiver = select.getExpression();
      @Nullable TreePath receiverPath = TreePath.getPath(at.getCompilationUnit(), receiver);
      if (receiverPath != null) {
        String type = ctx.qualifiedNameOf(ctx.typeOf(receiverPath));
        String simple = type.substring(type.lastIndexOf('.') + 1);
        if (MODEL_TYPES.stream().anyMatch(simple::endsWith)) {
          return true;
        }
      }
      current = NullFacts.unwrap(receiver);
    }
    return false;
  }

  private static @Nullable ExpressionTree localInitializer(TreePath at, String name) {
    @Nullable BlockTree body = null;
    for (TreePath p = at; p != null; p = p.getParentPath()) {
      if (p.getLeaf() instanceof MethodTree method) {
        body = method.getBody();
        break;
      }
      if (p.getLeaf() instanceof ClassTree) {
        break;
      }
    }
    if (body == null) {
      return null;
    }
    ExpressionTree[] found = new ExpressionTree[1];
    new TreePathScanner<Void, Void>() {
      @Override
      public @Nullable Void visitVariable(VariableTree variable, Void unused) {
        if (found[0] == null && variable.getName().contentEquals(name)) {
          found[0] = variable.getInitializer();
        }
        return super.visitVariable(variable, unused);
      }
    }.scan(TreePath.getPath(at.getCompilationUnit(), body), null);
    return found[0];
  }
}
