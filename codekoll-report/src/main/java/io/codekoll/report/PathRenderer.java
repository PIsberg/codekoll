package io.codekoll.report;

import java.nio.file.Path;

/**
 * Turns a finding's file path into the text that appears in output.
 *
 * <p>Findings carry absolute paths, because that is what the compiler hands the engine and what
 * makes a finding unambiguous internally. What a reader wants is the path relative to the
 * repository they are looking at — {@code src/main/java/Foo.java}, not
 * {@code C:\build\workspace\repo\src\main\java\Foo.java}. GitHub code scanning needs the same
 * thing: SARIF full of build-agent absolute paths annotates nothing.
 *
 * <p>The relativization itself lives with the workspace model, which is what knows where the repo
 * root is. This interface is how it reaches the reporters without {@code io.codekoll.report}
 * growing a dependency on discovery — the reporters stay usable with no workspace at all.
 */
@FunctionalInterface
public interface PathRenderer {

  /**
   * Renders one path.
   *
   * @param file the finding's file, absolute
   * @return the text to print
   */
  String render(Path file);

  /**
   * Absolute rendering: what codekoll emitted before repo-relative paths existed.
   *
   * @return a renderer that prints the path as-is
   */
  static PathRenderer absolute() {
    return Path::toString;
  }
}
