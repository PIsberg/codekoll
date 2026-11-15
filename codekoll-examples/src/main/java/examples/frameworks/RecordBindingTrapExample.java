package examples.frameworks;

/**
 * Example for rule {@code CK-RECORD-BINDING-TRAP}.
 *
 * <p><b>What is wrong:</b> {@link BuggyProperties} is a {@code @ConfigurationProperties} class with
 * a no-arg constructor, so Spring Boot binds it through setters, and its {@code timeoutSeconds}
 * field is {@code final}, so it has none. (The annotation is a stand-in for Spring Boot's;
 * codekoll matches by name.)
 *
 * <p><b>What happens at runtime:</b> {@code payment.timeout-seconds=5} in
 * {@code application.yml} is silently ignored: binding finds no setter, reports nothing, and the
 * field keeps its default of 30. Production runs with a timeout six times longer than configured,
 * and the configuration looks right to everyone who reads it.
 *
 * <p><b>How to fix it:</b> let Boot bind through the constructor, with a record, as
 * {@link FixedProperties} in {@link #fixed()} does.
 */
public class RecordBindingTrapExample {

  @interface ConfigurationProperties {
    String value() default "";
  }

  @ConfigurationProperties("payment")
  public static class BuggyProperties {
    private final int timeoutSeconds = 30; // :: CK-RECORD-BINDING-TRAP

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }
  }

  @ConfigurationProperties("payment")
  public record FixedProperties(int timeoutSeconds) {}

  public int buggy() {
    return new BuggyProperties().getTimeoutSeconds();
  }

  public int fixed() {
    return new FixedProperties(30).timeoutSeconds();
  }
}
