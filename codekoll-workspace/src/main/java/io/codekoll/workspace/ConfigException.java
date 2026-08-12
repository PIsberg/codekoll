package io.codekoll.workspace;

/**
 * A configuration file could not be used as written.
 *
 * <p>Always fatal, never a warning: every case that reaches here — a syntax error, an unknown key,
 * a value of the wrong type, a target repository trying to enable build execution — means the run
 * codekoll would perform is not the run the file asked for. Carrying on would analyze something
 * nobody specified, which is the failure this whole layer exists to avoid.
 */
public final class ConfigException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * @param message what is wrong, naming the file, the line where possible, and the key
   */
  public ConfigException(String message) {
    super(message);
  }

  /**
   * @param message what is wrong, naming the file, the line where possible, and the key
   * @param cause the parse or I/O failure underneath
   */
  public ConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
