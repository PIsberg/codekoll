package io.codekoll.loadtest;

import java.util.Locale;

/**
 * Names the kind of machine a measurement was taken on, so the gate compares like with like.
 *
 * <p>Performance numbers do not travel. The same unchanged analyzer measured 10 515 ms on the
 * development machine and 6 430 ms on a GitHub runner, with peak heap of 47 MB and 103 MB — the
 * latter because GC ergonomics size the heap from the machine, not from the work. Comparing across
 * that boundary produced regressions of +13.7 %, +15.8 % and +35.8 % on three pull requests that
 * could not have changed performance, one of which touched only Markdown.
 *
 * <p>Normalizing the numbers was tried and did not survive contact with CI (see
 * {@link Calibration}). So each environment carries its own baseline, and a run only ever compares
 * against an entry recorded on its own kind of machine. An environment with no entry does not
 * quietly pass: it fails and says how to record one.
 */
final class Environment {

  private Environment() {}

  /**
   * @return a short stable key such as {@code ci-linux} or {@code dev-windows}
   */
  static String key() {
    String os = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
    String family = os.startsWith("win") ? "windows"
        : os.startsWith("mac") ? "macos"
        : os.startsWith("linux") ? "linux"
        : "other";
    // GitHub Actions, GitLab CI, CircleCI and Travis all set CI=true; a developer machine does not.
    boolean ci = Boolean.parseBoolean(System.getenv("CI"));
    return (ci ? "ci-" : "dev-") + family;
  }
}
