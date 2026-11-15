package io.codekoll.rules.security;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

/**
 * CK-LLM-PROMPT-INJECTION, CK-LLM-UNSAFE-SINK and CK-LLM-API-KEY (issue #120). Spring AI,
 * LangChain4j, JdbcTemplate and SpEL types are local stand-ins: the rules match by type name.
 */
class LlmRulesTest {

  private static final String STUBS = """
        interface ChatClient {
          ChatClientRequestSpec prompt();
          interface ChatClientRequestSpec {
            ChatClientRequestSpec system(String text);
            ChatClientRequestSpec user(String text);
            CallResponseSpec call();
          }
          interface CallResponseSpec {
            String content();
          }
        }
        interface ChatLanguageModel {
          String generate(String prompt);
        }
        static class SystemMessage {
          SystemMessage(String text) {}
          static SystemMessage from(String text) {
            return new SystemMessage(text);
          }
        }
        static class PromptTemplate {
          PromptTemplate(String template) {}
          static PromptTemplate from(String template) {
            return new PromptTemplate(template);
          }
          String apply(java.util.Map<String, Object> variables) {
            return "";
          }
        }
        static class JdbcTemplate {
          void execute(String sql) {}
          java.util.List<Object> queryForList(String sql) {
            return java.util.List.of();
          }
        }
        static class SpelExpressionParser {
          Object parseExpression(String expression) {
            return expression;
          }
        }
      """;

  @Test
  void untrustedTextInSystemPromptFlagged() {
    RuleTestHarness.assertFixture(new LlmPromptInjectionRule(), "P1", """
        class P1 {
        %s
          SystemMessage langchain(String userName) {
            return SystemMessage.from("You are a helpful assistant for " + userName); // :: CK-LLM-PROMPT-INJECTION
          }
          SystemMessage springAi(String tone) {
            return new SystemMessage("Answer in a %%s tone".formatted(tone)); // :: CK-LLM-PROMPT-INJECTION
          }
          String chat(ChatClient chat, String question, String locale) {
            return chat.prompt()
                .system(String.format("Reply in %%s.", locale)) // :: CK-LLM-PROMPT-INJECTION
                .user(question)
                .call()
                .content();
          }
          PromptTemplate template(String rules) {
            return new PromptTemplate("Follow these rules: " + rules); // :: CK-LLM-PROMPT-INJECTION
          }
        }
        """.formatted(STUBS));
  }

  @Test
  void constantPromptsAndUserMessagesAllowed() {
    RuleTestHarness.assertFixture(new LlmPromptInjectionRule(), "N1", """
        import java.util.Map;
        class N1 {
        %s
          private static final String SYSTEM = "You are a helpful assistant.";
          String chat(ChatClient chat, String question) {
            return chat.prompt()
                .system(SYSTEM)
                .user("Question: " + question)
                .call()
                .content();
          }
          SystemMessage constant() {
            return SystemMessage.from("You are " + "concise.");
          }
          String template(String rules) {
            return PromptTemplate.from("Follow these rules: {rules}").apply(Map.of("rules", rules));
          }
          SystemMessage unknownOrigin(String prompt) {
            return SystemMessage.from(prompt);
          }
        }
        """.formatted(STUBS));
  }

  @Test
  void modelOutputExecutedFlagged() {
    RuleTestHarness.assertFixture(new LlmUnsafeSinkRule(), "P2", """
        import java.io.IOException;
        import java.sql.SQLException;
        import java.sql.Statement;
        import javax.script.ScriptEngine;
        import javax.script.ScriptException;
        class P2 {
        %s
          Object script(ChatClient chat, ScriptEngine js, String task) throws ScriptException {
            String code = chat.prompt().user(task).call().content();
            return js.eval(code); // :: CK-LLM-UNSAFE-SINK
          }
          Process shell(ChatLanguageModel model, String task) throws IOException {
            return new ProcessBuilder("sh", "-c", model.generate(task)).start(); // :: CK-LLM-UNSAFE-SINK
          }
          void sql(ChatLanguageModel model, JdbcTemplate jdbc, Statement statement, String question)
              throws SQLException {
            String query = model.generate("Write SQL for: " + question);
            jdbc.queryForList(query); // :: CK-LLM-UNSAFE-SINK
            statement.executeQuery(query); // :: CK-LLM-UNSAFE-SINK
          }
          Object spel(ChatLanguageModel model, SpelExpressionParser parser, String task) {
            return parser.parseExpression(model.generate(task)); // :: CK-LLM-UNSAFE-SINK
          }
        }
        """.formatted(STUBS));
  }

  @Test
  void validatedOrNonModelInputAllowed() {
    RuleTestHarness.assertFixture(new LlmUnsafeSinkRule(), "N2", """
        import java.util.Set;
        import javax.script.ScriptEngine;
        import javax.script.ScriptException;
        class N2 {
        %s
          private static final Set<String> REPORTS = Set.of("SELECT 1");
          static String allowListed(String sql) {
            if (!REPORTS.contains(sql)) {
              throw new IllegalArgumentException(sql);
            }
            return sql;
          }
          void validated(ChatLanguageModel model, JdbcTemplate jdbc, String question) {
            String query = model.generate(question);
            jdbc.execute(allowListed(query));
          }
          Object fixedScript(ScriptEngine js, String input) throws ScriptException {
            return js.eval("1 + 1");
          }
          String summary(ChatLanguageModel model, String text) {
            String reply = model.generate(text);
            return reply.trim();
          }
        }
        """.formatted(STUBS));
  }

  /**
   * Fake keys, assembled at runtime so that no key-shaped string is committed: secret scanners
   * would block the push or raise an alert on a literal in this file.
   */
  private static final String OPENAI = "sk-" + "proj-4fT9xQ2LmN8vR3kP7wZ1aB6cD0eF5gH2";
  private static final String ANTHROPIC = "sk-" + "ant-api03-Zx9Qw8Er7Ty6Ui5Op4As3Df2Gh1Jk0Lz";
  private static final String GOOGLE = "AI" + "zaSyB1c2D3e4F5g6H7i8J9k0L1m2N3o4P5q6R";

  @Test
  void providerKeyLiteralsFlagged() {
    RuleTestHarness.assertFixture(new LlmApiKeyRule(), "P3", """
        import java.util.Map;
        class P3 {
          private static final String OPENAI_KEY = "%s"; // :: CK-LLM-API-KEY
          private final String anthropicKey = "%s"; // :: CK-LLM-API-KEY
          Map<String, String> headers() {
            return Map.of("x-goog-api-key", "%s"); // :: CK-LLM-API-KEY
          }
        }
        """.formatted(OPENAI, ANTHROPIC, GOOGLE));
  }

  @Test
  void placeholdersShortValuesAndNamedSecretsAllowed() {
    RuleTestHarness.assertFixture(new LlmApiKeyRule(), "N3", """
        class N3 {
          private static final String OPENAI_KEY = "sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
          private static final String EXAMPLE = "sk-your-openai-key-goes-here-123456";
          private static final String SKETCH = "sk-short";
          private static final String TASK = "sketch-the-architecture-diagram-for-review";
          private static final String API_TOKEN = "%s";
        }
        """.formatted(OPENAI));
  }
}
