package examples.security;

/**
 * Example for rule {@code CK-HARDCODED-SECRET}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} carries the production database password as a
 * string literal.
 *
 * <p><b>What happens at runtime:</b> nothing fails — that is the problem. The secret ships
 * in version-control history (forever, even after "removal"), in every build artifact, and
 * in any decompiled jar. Everyone with repo read access holds production credentials, and
 * rotating them requires a code release.
 *
 * <p><b>How to fix it:</b> load the secret from the environment or a vault at runtime, as
 * {@link #fixed()} does — only the lookup key lives in code.
 */
public class HardcodedSecretExample {

  public String buggy() {
    String dbPassword = "s3cr3t-prod-2024"; // :: CK-HARDCODED-SECRET
    return connect(dbPassword);
  }

  public String fixed() {
    String dbPassword = System.getenv("DB_PASSWORD");
    return connect(dbPassword);
  }

  private String connect(String password) {
    return password == null ? "no credentials" : "connected";
  }
}
