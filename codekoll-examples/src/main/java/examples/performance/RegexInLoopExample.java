package examples.performance;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Example for rule {@code CK-REGEX-IN-LOOP}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(List)} compiles the same constant regex on every
 * loop iteration.
 *
 * <p><b>What happens at runtime:</b> Pattern.compile parses the expression and builds its
 * automaton each call — for a million log lines, a million identical compilations. This is
 * a top-three profiler finding in text-processing hot paths.
 *
 * <p><b>How to fix it:</b> hoist the pattern to a {@code static final Pattern}, as
 * {@link #fixed(List)} does.
 */
public class RegexInLoopExample {

  private static final Pattern ERROR_LINE = Pattern.compile("ERROR\\s+\\[(\\w+)]");

  public int buggy(List<String> logLines) {
    int errors = 0;
    for (String line : logLines) {
      if (Pattern.compile("ERROR\\s+\\[(\\w+)]").matcher(line).find()) { // :: CK-REGEX-IN-LOOP
        errors++;
      }
    }
    return errors;
  }

  public int fixed(List<String> logLines) {
    int errors = 0;
    for (String line : logLines) {
      if (ERROR_LINE.matcher(line).find()) {
        errors++;
      }
    }
    return errors;
  }
}
