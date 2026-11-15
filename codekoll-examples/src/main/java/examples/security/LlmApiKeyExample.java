package examples.security;

/**
 * Example for rule {@code CK-LLM-API-KEY}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} returns an OpenAI-style key written into the source.
 * The variable is called {@code OPENAI_KEY}, a name CK-HARDCODED-SECRET does not match, so only
 * the value gives it away. (The key is made up.)
 *
 * <p><b>What happens at runtime:</b> nothing visible: the feature works. The key is in version
 * control and in every jar from now on. Anyone with read access can run up usage on the account;
 * providers scan public code and revoke leaked keys, which takes the feature down in production
 * the day the repository or an artifact becomes public.
 *
 * <p><b>How to fix it:</b> read the key from the environment, as {@link #fixed()} does, and
 * rotate the leaked one: it stays in the history.
 */
public class LlmApiKeyExample {

  private static final String OPENAI_KEY = "sk-proj-DemoKeyForCodekollDocs0123456789abcd"; // :: CK-LLM-API-KEY

  public String buggy() {
    return OPENAI_KEY;
  }

  public String fixed() {
    return System.getenv("OPENAI_API_KEY");
  }
}
