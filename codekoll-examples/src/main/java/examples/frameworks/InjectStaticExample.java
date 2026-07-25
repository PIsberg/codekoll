package examples.frameworks;

/**
 * Example for rule {@code CK-INJECT-STATIC}.
 *
 * <p><b>What is wrong:</b> the {@code buggy} configuration field is {@code static} and
 * annotated for injection. (The annotation is a stand-in for Spring's; codekoll matches by
 * name.)
 *
 * <p><b>What happens at runtime:</b> DI containers inject INSTANCE state and silently skip
 * static fields. The field keeps its null default; the first use throws
 * NullPointerException far from this declaration, which looks perfectly wired.
 *
 * <p><b>How to fix it:</b> instance injection — constructor injection preferred — as the
 * {@code fixed} field shows.
 */
public class InjectStaticExample {

  @interface Autowired {}

  @Autowired // :: CK-INJECT-STATIC
  private static String buggyServiceUrl;

  @Autowired
  private String fixedServiceUrl;

  /** The corrected form: an instance field, which the container actually injects. */
  String fixed() {
    return fixedServiceUrl;
  }

  String describe() {
    return buggyServiceUrl + "/" + fixed();
  }
}
