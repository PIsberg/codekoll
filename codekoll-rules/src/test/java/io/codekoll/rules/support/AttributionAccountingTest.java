package io.codekoll.rules.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.engine.Attribution;
import io.codekoll.engine.CompilationDriver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How much of a run javac could type-check, and what it could not resolve (CLI-SPEC §6).
 *
 * <p>A file that fails attribution gets no rule at all, so without this tally a run over a
 * repository whose dependencies are missing looks exactly like a clean one. These cases pin the
 * counts the CLI's footer is built from.
 */
class AttributionAccountingTest {

  @TempDir
  Path dir;

  private Attribution attributionOf(Map<String, String> sources) throws IOException {
    for (Map.Entry<String, String> source : sources.entrySet()) {
      Files.writeString(dir.resolve(source.getKey() + ".java"), source.getValue(),
          StandardCharsets.UTF_8);
    }
    return new CompilationDriver(25, "").analyzePaths(List.of(dir), List.of()).attribution();
  }

  @Test
  void aRunThatTypeChecksEverythingIsComplete() throws IOException {
    Attribution attribution = attributionOf(Map.of("Ok", "class Ok {}"));

    assertEquals(1, attribution.discovered());
    assertEquals(1, attribution.attributed());
    assertTrue(attribution.complete());
    assertEquals(Map.of(), attribution.unresolved());
  }

  @Test
  void unresolvedPackagesAreCountedPerFileMostCommonFirst() throws IOException {
    Attribution attribution = attributionOf(Map.of(
        "Ok", "class Ok {}",
        "A", "import org.missing.one.Thing; class A { Thing t; }",
        "B", "import org.missing.one.Other; import com.absent.Lib; class B { Other o; Lib l; }",
        "C", "import org.missing.one.Thing; import org.missing.one.Other; class C {}"));

    assertEquals(4, attribution.discovered());
    assertEquals(1, attribution.attributed());
    assertEquals(3, attribution.failed());
    assertFalse(attribution.complete());
    // C imports two classes from the package and still counts once: the number is files affected.
    assertEquals(List.of("org.missing.one", "com.absent"),
        List.copyOf(attribution.unresolved().keySet()));
    assertEquals(3, attribution.unresolved().get("org.missing.one"));
    assertEquals(1, attribution.unresolved().get("com.absent"));
  }

  @Test
  void aClassMissingFromAPackageThatExistsIsNamedInFull() throws IOException {
    Attribution attribution =
        attributionOf(Map.of("D", "import java.util.NoSuchThing; class D {}"));

    assertEquals(Map.of("java.util.NoSuchThing", 1), attribution.unresolved());
  }

  @Test
  void aSyntaxErrorCountsAsUnattributedButNamesNothing() throws IOException {
    Attribution attribution = attributionOf(Map.of("Ok", "class Ok {}", "E", "class E {"));

    assertEquals(2, attribution.discovered());
    assertEquals(1, attribution.attributed());
    assertEquals(Map.of(), attribution.unresolved());
  }

  @Test
  void coverageNeverRoundsUpToComplete() {
    // 762 of 765 is 99.6 %. Rounded, the footer would claim 100 % while three files went
    // unchecked, which is the exact confusion the footer exists to prevent.
    assertEquals(99, new Attribution(765, 762, Map.of()).coveragePercent());
    assertEquals(100, new Attribution(765, 765, Map.of()).coveragePercent());
    assertEquals(100, Attribution.empty().coveragePercent());
  }

  @Test
  void combiningTalliesAddsCountsAndKeepsMostCommonFirst() {
    Attribution first = new Attribution(10, 8, Map.of("org.a", 2));
    Attribution second = new Attribution(5, 2, Map.of("org.b", 1, "org.a", 1, "org.c", 2));

    Attribution both = first.plus(second);

    assertEquals(15, both.discovered());
    assertEquals(10, both.attributed());
    assertEquals(List.of("org.a", "org.c", "org.b"), List.copyOf(both.unresolved().keySet()));
    assertEquals(3, both.unresolved().get("org.a"));
  }
}
