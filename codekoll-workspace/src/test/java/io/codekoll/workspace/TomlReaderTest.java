package io.codekoll.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.workspace.TomlReader.Entry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The reader accepts a closed subset and refuses everything else by name (CLI-SPEC §10).
 *
 * <p>The hostile half of this suite matters more than the happy half. A configuration reader that
 * guesses turns a typo into a silently different analysis: a misspelled table means a whole
 * section of settings is ignored, and a rule that was meant to be disabled keeps firing — or
 * worse, one that was meant to stay on goes quiet.
 */
class TomlReaderTest {

  private static Map<String, Entry> parse(String text) {
    return TomlReader.parse("test.toml", text);
  }

  private static Object value(Map<String, Entry> entries, String key) {
    Entry entry = entries.get(key);
    assertTrue(entry != null, "no entry '" + key + "' in " + entries.keySet());
    return entry.value();
  }

  private static String messageOf(String text) {
    return assertThrows(TomlReader.TomlException.class, () -> parse(text)).getMessage();
  }

  // ------------------------------------------------------------- the subset

  @Test
  void readsTheSchemaFromTheSpec() {
    Map<String, Entry> entries = parse("""
        [rules]
        disable = ["CK-CRYPTO-WEAK"]
        disable-packs = ["performance"]
        enable-only = []

        [severity]
        "CK-THREAD-RUN" = "error"

        [sources]
        tests = false
        gitignore = true

        [compile]
        release = 21

        [report]
        min-attribution = 90
        """);

    assertEquals(List.of("CK-CRYPTO-WEAK"), value(entries, "rules.disable"));
    assertEquals(List.of("performance"), value(entries, "rules.disable-packs"));
    assertEquals(List.of(), value(entries, "rules.enable-only"));
    assertEquals("error", value(entries, "severity.\"CK-THREAD-RUN\""));
    assertEquals(Boolean.FALSE, value(entries, "sources.tests"));
    assertEquals(Boolean.TRUE, value(entries, "sources.gitignore"));
    assertEquals(21, value(entries, "compile.release"));
    assertEquals(90, value(entries, "report.min-attribution"));
  }

  @Test
  void keepsDocumentOrderAndLineNumbers() {
    Map<String, Entry> entries = parse("""
        [rules]
        disable = []

        [compile]
        release = 17
        """);

    assertEquals(List.of("rules.disable", "compile.release"), List.copyOf(entries.keySet()));
    assertEquals(2, entries.get("rules.disable").line());
    assertEquals(5, entries.get("compile.release").line());
  }

  @Test
  void readsArraysThatSpanLines() {
    Map<String, Entry> entries = parse("""
        [suppress]
        paths = [
          "**/generated/**",
          "**/legacy/**",
        ]
        tests = true
        """);

    assertEquals(List.of("**/generated/**", "**/legacy/**"), value(entries, "suppress.paths"));
    assertEquals(Boolean.TRUE, value(entries, "suppress.tests"), "the line after the array");
  }

  @Test
  void commentsAndBlankLinesAreIgnoredButNotInsideStrings() {
    Map<String, Entry> entries = parse("""
        # leading comment
        [suppress]

        paths = ["**/#not-a-comment/**"]  # trailing comment
        """);

    assertEquals(List.of("**/#not-a-comment/**"), value(entries, "suppress.paths"));
  }

  @Test
  void aCommaInsideAGlobDoesNotSplitTheArray() {
    Map<String, Entry> entries = parse("""
        [suppress]
        paths = ["**/{a,b}/**", "x"]
        """);

    assertEquals(List.of("**/{a,b}/**", "x"), value(entries, "suppress.paths"));
  }

  @Test
  void escapesAreDecoded() {
    Map<String, Entry> entries = parse("""
        [compile]
        classpath = "C:\\\\libs\\\\foo.jar"
        """);

    assertEquals("C:\\libs\\foo.jar", value(entries, "compile.classpath"));
  }

  // -------------------------------------------------------- hostile input

  @Test
  void aDuplicateKeyIsAnErrorRatherThanALastOneWins() {
    assertTrue(messageOf("""
        [rules]
        disable = ["A"]
        disable = ["B"]
        """).contains("duplicate key 'rules.disable'"));
  }

  @Test
  void theErrorNamesTheLine() {
    assertTrue(messageOf("""
        [rules]
        disable = ["A"]
        broken
        """).contains("test.toml:3"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "[rules\ndisable = []",              // unterminated header
      "[[rules]]\ndisable = []",           // array of tables
      "[rules.nested]\ndisable = []",      // nested table
      "[]\ndisable = []",                  // empty table name
      "[rules]\ndisable",                  // no '='
      "[rules]\ndisable =",                // no value
      "[rules]\ndisable = [\"a\"",         // unterminated array
      "[rules]\ndisable = [[\"a\"]]",      // nested array
      "[rules]\nname = 'single'",          // literal string
      "[rules]\nname = \"\"\"multi\"\"\"", // multi-line string
      "[rules]\nname = \"unterminated",    // unterminated string
      "[rules]\nname = { a = 1 }",         // inline table
      "[rules]\na.b = 1",                  // dotted key
      "[rules]\nrelease = 1.5",            // float
      "[rules]\nname = \"a\\q\"",          // unsupported escape
  })
  void refusesWhatItDoesNotSupport(String text) {
    TomlReader.TomlException e =
        assertThrows(TomlReader.TomlException.class, () -> parse(text), text);

    assertTrue(e.getMessage().startsWith("test.toml:"), e.getMessage());
  }

  @Test
  void anEmptyDocumentIsValidAndEmpty() {
    assertEquals(Map.of(), parse(""));
    assertEquals(Map.of(), parse("# nothing but a comment\n"));
  }
}
