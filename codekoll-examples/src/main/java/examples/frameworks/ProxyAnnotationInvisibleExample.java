package examples.frameworks;

/**
 * Example for rule {@code CK-PROXY-ANNOTATION-INVISIBLE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} is annotated {@code @Transactional} — and is
 * private. (The annotation here is a stand-in for Spring's; codekoll matches by name.)
 *
 * <p><b>What happens at runtime:</b> Spring applies @Transactional through a runtime proxy
 * that overrides the method — impossible for a private method. The annotation is SILENTLY
 * ignored: the transfer runs with no transaction, and the rollback that should undo the
 * debit on failure never happens. Nothing throws, nothing logs.
 *
 * <p><b>How to fix it:</b> make the method public and non-final so the proxy can intercept
 * it, as {@link #fixed()} is.
 */
public class ProxyAnnotationInvisibleExample {

  @interface Transactional {}

  private int balance = 100;

  @Transactional // :: CK-PROXY-ANNOTATION-INVISIBLE
  private void buggy() {
    balance -= 50;
  }

  @Transactional
  public void fixed() {
    balance -= 50;
  }

  int balance() {
    return balance;
  }
}
