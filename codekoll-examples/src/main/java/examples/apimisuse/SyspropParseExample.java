package examples.apimisuse;

import java.util.Properties;

/**
 * Example for rule {@code CK-SYSPROP-PARSE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Properties)} reads the audit flag out of a
 * {@code Properties} file and then converts it with {@code Boolean.getBoolean(enabled)}.
 * Despite the name, {@code Boolean.getBoolean} does not parse its argument — it treats it as
 * the <em>name</em> of a system property.
 *
 * <p><b>What happens at runtime:</b> the call is really
 * {@code "true".equalsIgnoreCase(System.getProperty("true"))}. No system property called
 * {@code "true"} exists, so the method returns {@code false} — every time, on every machine,
 * no matter what the config file says. Auditing is simply never enabled, and because nothing
 * throws and nothing is logged, the flag looks wired up in code review and in the config file.
 * ({@code Integer.getInteger}/{@code Long.getLong} are the same trap and return {@code null},
 * which then throws {@code NullPointerException} on unboxing.)
 *
 * <p><b>How to fix it:</b> use the parsing methods — {@code Boolean.parseBoolean},
 * {@code Integer.parseInt}, {@code Long.parseLong} — as {@link #fixed(Properties)} does, and
 * keep {@code getBoolean}/{@code getInteger}/{@code getLong} for actual system-property names.
 */
public class SyspropParseExample {

  public boolean buggy(Properties config) {
    String enabled = config.getProperty("acme.audit.enabled", "false");
    return Boolean.getBoolean(enabled); // :: CK-SYSPROP-PARSE
  }

  public boolean fixed(Properties config) {
    String enabled = config.getProperty("acme.audit.enabled", "false");
    return Boolean.parseBoolean(enabled);
  }
}
