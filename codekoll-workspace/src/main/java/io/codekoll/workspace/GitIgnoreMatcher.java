package io.codekoll.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A deliberately small {@code .gitignore} reader: the subset of the format that real repositories
 * actually use — comments, blank lines, {@code !} negation, trailing {@code /} for
 * directory-only, leading {@code /} for repo-anchored, and the {@code *} / {@code ?} / {@code **}
 * wildcards.
 *
 * <p>Codekoll does not shell out to git for this, so it works on an exported tree with no git
 * installed. Patterns it cannot translate are skipped with a diagnostic rather than guessed at:
 * over-matching an ignore rule would silently drop files from analysis, which is exactly the
 * failure this module refuses to make.
 */
final class GitIgnoreMatcher {

  private final List<Entry> entries;

  private GitIgnoreMatcher(List<Entry> entries) {
    this.entries = entries;
  }

  /** An always-false matcher, for when {@code .gitignore} handling is switched off. */
  static GitIgnoreMatcher none() {
    return new GitIgnoreMatcher(List.of());
  }

  /**
   * Reads {@code <repoRoot>/.gitignore} plus {@code .git/info/exclude} if present.
   *
   * @param repoRoot repository root
   * @param diagnostics collector for unparseable patterns
   */
  static GitIgnoreMatcher forRepo(Path repoRoot, List<String> diagnostics) {
    List<Entry> entries = new ArrayList<>();
    read(repoRoot.resolve(".gitignore"), entries, diagnostics);
    read(repoRoot.resolve(".git").resolve("info").resolve("exclude"), entries, diagnostics);
    return new GitIgnoreMatcher(entries);
  }

  private static void read(Path file, List<Entry> entries, List<String> diagnostics) {
    if (!Files.isRegularFile(file)) {
      return;
    }
    List<String> lines;
    try {
      lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      diagnostics.add("could not read " + file + ": " + e.getMessage());
      return;
    }
    for (String raw : lines) {
      String line = raw.strip();
      if (line.isEmpty() || line.charAt(0) == '#') {
        continue;
      }
      boolean negated = line.charAt(0) == '!';
      if (negated) {
        line = line.substring(1);
      }
      boolean dirOnly = line.endsWith("/");
      if (dirOnly) {
        line = line.substring(0, line.length() - 1);
      }
      if (line.isEmpty()) {
        continue;
      }
      try {
        entries.add(new Entry(Pattern.compile(toRegex(line)), negated, dirOnly));
      } catch (PatternSyntaxException e) {
        diagnostics.add("skipped unsupported .gitignore pattern '" + raw.strip() + "'");
      }
    }
  }

  /**
   * Translates one gitignore pattern into a regex matched against the repo-relative,
   * {@code /}-separated path.
   */
  private static String toRegex(String pattern) {
    boolean anchored = pattern.indexOf('/') >= 0;
    String body = pattern.startsWith("/") ? pattern.substring(1) : pattern;
    StringBuilder sb = new StringBuilder(body.length() * 2);
    // An unanchored pattern ("build") matches at any depth; an anchored one ("src/build")
    // matches from the repo root only.
    sb.append(anchored ? "\\A" : "(\\A|.*/)");
    int i = 0;
    while (i < body.length()) {
      char c = body.charAt(i);
      if (c == '*' && i + 1 < body.length() && body.charAt(i + 1) == '*') {
        sb.append(".*");
        i += 2;
        if (i < body.length() && body.charAt(i) == '/') {
          i++;
        }
      } else if (c == '*') {
        sb.append("[^/]*");
        i++;
      } else if (c == '?') {
        sb.append("[^/]");
        i++;
      } else {
        sb.append(Pattern.quote(String.valueOf(c)));
        i++;
      }
    }
    // The pattern matches the entry itself and everything beneath it.
    sb.append("(/.*)?\\z");
    return sb.toString();
  }

  /** True when git would ignore this repo-relative path. Later entries win, as git does. */
  boolean isIgnored(String relativePath, boolean directory) {
    boolean ignored = false;
    for (Entry entry : entries) {
      if (entry.dirOnly() && !directory) {
        continue;
      }
      if (entry.pattern().matcher(relativePath).matches()) {
        ignored = !entry.negated();
      }
    }
    return ignored;
  }

  private record Entry(Pattern pattern, boolean negated, boolean dirOnly) {}
}
