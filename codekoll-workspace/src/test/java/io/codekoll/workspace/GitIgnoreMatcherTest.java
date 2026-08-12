package io.codekoll.workspace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code .gitignore} subset codekoll implements itself rather than shelling out to git.
 *
 * <p>Over-matching here silently drops files from analysis, so the negative assertions matter as
 * much as the positive ones.
 */
class GitIgnoreMatcherTest {

  @TempDir
  Path repo;

  private final List<String> diagnostics = new ArrayList<>();

  private GitIgnoreMatcher matcher(String... lines) throws IOException {
    Files.writeString(repo.resolve(".gitignore"),
        String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    return GitIgnoreMatcher.forRepo(repo, diagnostics);
  }

  @Test
  void noneIgnoresNothing() {
    assertFalse(GitIgnoreMatcher.none().isIgnored("anything/at/all.java", false));
  }

  @Test
  void absentGitignoreIgnoresNothing() {
    assertFalse(GitIgnoreMatcher.forRepo(repo, diagnostics).isIgnored("src/A.java", false));
    assertTrue(diagnostics.isEmpty());
  }

  @Test
  void unanchoredPatternMatchesAtAnyDepth() throws IOException {
    GitIgnoreMatcher m = matcher("build");

    assertTrue(m.isIgnored("build", true));
    assertTrue(m.isIgnored("a/b/build", true));
    assertTrue(m.isIgnored("a/build/Generated.java", false));
    assertFalse(m.isIgnored("src/rebuild/A.java", false), "must match a whole segment, not a prefix");
  }

  @Test
  void anchoredPatternMatchesFromTheRootOnly() throws IOException {
    GitIgnoreMatcher m = matcher("/target");

    assertTrue(m.isIgnored("target", true));
    assertFalse(m.isIgnored("module/target", true));
  }

  @Test
  void patternWithASlashIsAnchored() throws IOException {
    GitIgnoreMatcher m = matcher("src/generated");

    assertTrue(m.isIgnored("src/generated/A.java", false));
    assertFalse(m.isIgnored("other/src/generated/A.java", false));
  }

  @Test
  void directoryOnlyPatternDoesNotMatchAFile() throws IOException {
    GitIgnoreMatcher m = matcher("vendor/");

    assertTrue(m.isIgnored("vendor", true));
    assertFalse(m.isIgnored("vendor", false), "a trailing slash means directories only");
  }

  @Test
  void singleStarDoesNotCrossDirectories() throws IOException {
    GitIgnoreMatcher m = matcher("*.tmp.java");

    assertTrue(m.isIgnored("A.tmp.java", false));
    assertTrue(m.isIgnored("pkg/A.tmp.java", false));
    assertFalse(m.isIgnored("A.java", false));
  }

  @Test
  void doubleStarCrossesDirectories() throws IOException {
    GitIgnoreMatcher m = matcher("src/**/generated");

    assertTrue(m.isIgnored("src/main/java/generated/A.java", false));
  }

  @Test
  void questionMarkMatchesOneCharacter() throws IOException {
    GitIgnoreMatcher m = matcher("A?.java");

    assertTrue(m.isIgnored("A1.java", false));
    assertFalse(m.isIgnored("A12.java", false));
  }

  @Test
  void negationReinstatesAnIgnoredPath() throws IOException {
    GitIgnoreMatcher m = matcher("generated", "!generated/Keep.java");

    assertTrue(m.isIgnored("generated/Other.java", false));
    assertFalse(m.isIgnored("generated/Keep.java", false), "later entries win, as git does");
  }

  @Test
  void commentsAndBlankLinesAreSkipped() throws IOException {
    GitIgnoreMatcher m = matcher("# a comment", "", "   ", "build");

    assertTrue(m.isIgnored("build", true));
    assertFalse(m.isIgnored("a-comment", false));
  }

  @Test
  void gitInfoExcludeIsReadToo() throws IOException {
    Path exclude = repo.resolve(".git").resolve("info").resolve("exclude");
    Files.createDirectories(exclude.getParent());
    Files.writeString(exclude, "scratch/\n", StandardCharsets.UTF_8);

    assertTrue(GitIgnoreMatcher.forRepo(repo, diagnostics).isIgnored("scratch", true));
  }
}
