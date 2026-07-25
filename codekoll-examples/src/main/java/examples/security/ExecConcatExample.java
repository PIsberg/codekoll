package examples.security;

/**
 * Example for rule {@code CK-EXEC-CONCAT}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} builds a shell command by concatenating
 * {@code filename} into the command string.
 *
 * <p><b>What happens at runtime:</b> the variable becomes part of the COMMAND. A filename
 * of {@code "x.png; rm -rf /"} runs the attacker's command with the application's
 * privileges — command injection, which is remote code execution whenever any part of the
 * string is user-influenced.
 *
 * <p><b>How to fix it:</b> the list form, where each argument is separate and never parsed
 * as shell syntax, as {@link #fixed(String)} does.
 */
public class ExecConcatExample {

  public Process buggy(String filename) throws Exception {
    return Runtime.getRuntime().exec("convert " + filename + " thumb.png"); // :: CK-EXEC-CONCAT
  }

  public Process fixed(String filename) throws Exception {
    return new ProcessBuilder("convert", filename, "thumb.png").start();
  }
}
