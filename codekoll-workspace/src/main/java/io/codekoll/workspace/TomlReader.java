package io.codekoll.workspace;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the strict subset of TOML that {@code codekoll.toml} uses (CLI-SPEC §10).
 *
 * <p>Hand-rolled on purpose. The schema is small and closed — tables one level deep, string,
 * integer, boolean and array-of-string values — and codekoll ships as a single jar whose one
 * runtime dependency is the CLI parser. A second dependency to read forty lines of configuration
 * is a poor trade, and the parser a full TOML implementation gives us would accept far more than
 * the schema allows, turning typos into valid documents.
 *
 * <p>Deliberately <em>not</em> supported, each rejected with a message rather than misread:
 * dotted keys, inline tables, arrays of tables, multi-line strings, literal strings, dates,
 * floats, and nested arrays. A config file using any of them is a config file written against a
 * different tool.
 *
 * <p>Keys come back dotted — {@code rules.disable}, {@code severity."CK-THREAD-RUN"} — in the
 * order they appeared, each carrying the line it was read from so that errors can point at it.
 */
final class TomlReader {

  /** One key/value pair, with the line it came from for error messages. */
  record Entry(String key, Object value, int line) {}

  /** What the reader refuses to guess at. Carries a message a user can act on. */
  static final class TomlException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    TomlException(String message) {
      super(message);
    }

    TomlException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private final String source;
  private final Map<String, Entry> entries = new LinkedHashMap<>();
  private String table = "";
  private int lineNumber;

  private TomlReader(String source) {
    this.source = source;
  }

  /**
   * Parses a TOML file.
   *
   * @param file the file to read
   * @return entries in document order, keyed by dotted key
   * @throws TomlException if the file cannot be read or is not in the supported subset
   */
  static Map<String, Entry> read(Path file) {
    String text;
    try {
      text = Files.readString(file, StandardCharsets.UTF_8);
    } catch (MalformedInputException e) {
      throw new TomlException(file + " is not valid UTF-8", e);
    } catch (IOException e) {
      throw new TomlException("could not read " + file + ": " + e.getMessage(), e);
    }
    return new TomlReader(file.toString()).parse(text);
  }

  /** Parses TOML text directly, for tests and for configuration held in memory. */
  static Map<String, Entry> parse(String source, String text) {
    return new TomlReader(source).parse(text);
  }

  private Map<String, Entry> parse(String text) {
    List<String> lines = text.lines().toList();
    int index = 0;
    while (index < lines.size()) {
      lineNumber = index + 1;
      String line = stripComment(lines.get(index)).strip();
      if (line.isEmpty()) {
        index++;
      } else if (line.charAt(0) == '[') {
        readTableHeader(line);
        index++;
      } else {
        readPair(line, lines, index);
        index = skipContinuation(lines, index) + 1;
      }
    }
    return entries;
  }

  private void readTableHeader(String line) {
    if (line.startsWith("[[")) {
      throw error("arrays of tables are not supported");
    }
    if (!line.endsWith("]")) {
      throw error("unterminated table header");
    }
    String name = line.substring(1, line.length() - 1).strip();
    if (name.isEmpty()) {
      throw error("empty table name");
    }
    if (name.indexOf('.') >= 0) {
      throw error("nested tables are not supported: [" + name + "]");
    }
    if (!isBareKey(name)) {
      throw error("table name is not a bare key: [" + name + "]");
    }
    table = name;
  }

  private void readPair(String line, List<String> lines, int index) {
    int equals = indexOfEquals(line);
    if (equals < 0) {
      throw error("expected 'key = value'");
    }
    String key = line.substring(0, equals).strip();
    String rawValue = line.substring(equals + 1).strip();
    if (rawValue.startsWith("[") && !rawValue.endsWith("]")) {
      rawValue = joinArray(lines, index);
    }
    String dotted = qualify(key);
    if (entries.containsKey(dotted)) {
      throw error("duplicate key '" + dotted + "'");
    }
    entries.put(dotted, new Entry(dotted, value(rawValue), lineNumber));
  }

  /** Multi-line arrays are the one construct that spans lines; skip what {@link #joinArray} ate. */
  private int skipContinuation(List<String> lines, int index) {
    int depth = 0;
    for (int i = index; i < lines.size(); i++) {
      String line = stripComment(lines.get(i));
      depth += count(line, '[') - count(line, ']');
      if (depth <= 0) {
        return i;
      }
    }
    return lines.size() - 1;
  }

  private String joinArray(List<String> lines, int index) {
    StringBuilder joined = new StringBuilder();
    int depth = 0;
    for (int i = index; i < lines.size(); i++) {
      String line = stripComment(lines.get(i));
      joined.append(i == index ? line.substring(indexOfEquals(line) + 1) : line).append(' ');
      depth += count(line, '[') - count(line, ']');
      if (depth <= 0) {
        return joined.toString().strip();
      }
    }
    throw error("unterminated array");
  }

  private static int count(String text, char c) {
    int n = 0;
    boolean inString = false;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
        inString = !inString;
      } else if (!inString && ch == c) {
        n++;
      }
    }
    return n;
  }

  private String qualify(String key) {
    String name = key;
    if (name.length() >= 2 && name.charAt(0) == '"' && name.endsWith("\"")) {
      name = '"' + unescape(name.substring(1, name.length() - 1)) + '"';
    } else if (name.indexOf('.') >= 0) {
      throw error("dotted keys are not supported: " + key);
    } else if (!isBareKey(name)) {
      throw error("key is neither a bare key nor a quoted string: " + key);
    }
    return table.isEmpty() ? name : table + "." + name;
  }

  private Object value(String raw) {
    if (raw.isEmpty()) {
      throw error("missing value");
    }
    char first = raw.charAt(0);
    if (first == '"') {
      return string(raw);
    }
    if (first == '\'') {
      throw error("literal strings are not supported; use \"double quotes\"");
    }
    if (first == '[') {
      return array(raw);
    }
    if ("true".equals(raw) || "false".equals(raw)) {
      return Boolean.valueOf(raw);
    }
    if (raw.indexOf('{') >= 0) {
      throw error("inline tables are not supported");
    }
    return integer(raw);
  }

  private String string(String raw) {
    if (raw.startsWith("\"\"\"")) {
      throw error("multi-line strings are not supported");
    }
    int end = closingQuote(raw);
    if (end < 0) {
      throw error("unterminated string");
    }
    if (end != raw.length() - 1) {
      throw error("unexpected text after the closing quote: " + raw.substring(end + 1).strip());
    }
    return unescape(raw.substring(1, end));
  }

  /** Index of the quote that closes the string opened at index 0, or {@code -1} if there is none. */
  private static int closingQuote(String raw) {
    int i = 1;
    while (i < raw.length()) {
      char c = raw.charAt(i);
      if (c == '"') {
        return i;
      }
      i += c == '\\' ? 2 : 1;
    }
    return -1;
  }

  private Integer integer(String raw) {
    try {
      return Integer.valueOf(raw.replace("_", ""));
    } catch (NumberFormatException e) {
      throw error("not a supported value: " + raw
          + " (expected a string, integer, boolean, or array of strings)", e);
    }
  }

  private List<Object> array(String raw) {
    if (!raw.endsWith("]")) {
      throw error("unterminated array");
    }
    String body = raw.substring(1, raw.length() - 1).strip();
    List<Object> values = new ArrayList<>();
    if (body.isEmpty()) {
      return values;
    }
    for (String element : splitElements(body)) {
      String item = element.strip();
      if (item.isEmpty()) {
        continue;
      }
      if (item.startsWith("[")) {
        throw error("nested arrays are not supported");
      }
      values.add(value(item));
    }
    return values;
  }

  /** Splits on commas that are outside strings, so a comma inside a glob survives. */
  private static List<String> splitElements(String body) {
    List<String> parts = new ArrayList<>();
    boolean inString = false;
    int start = 0;
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
        inString = !inString;
      } else if (c == ',' && !inString) {
        parts.add(body.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(body.substring(start));
    return parts;
  }

  /** Removes a trailing comment, leaving {@code #} inside a string alone. */
  private static String stripComment(String line) {
    boolean inString = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
        inString = !inString;
      } else if (c == '#' && !inString) {
        return line.substring(0, i);
      }
    }
    return line;
  }

  /** Finds the {@code =} that separates key from value, ignoring one inside a quoted key. */
  private static int indexOfEquals(String line) {
    boolean inString = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
        inString = !inString;
      } else if (c == '=' && !inString) {
        return i;
      }
    }
    return -1;
  }

  private String unescape(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c != '\\') {
        sb.append(c);
        i++;
        continue;
      }
      if (i + 1 >= text.length()) {
        throw error("dangling escape at end of string");
      }
      char next = text.charAt(i + 1);
      i += 2;
      switch (next) {
        case '"' -> sb.append('"');
        case '\\' -> sb.append('\\');
        case 'n' -> sb.append('\n');
        case 't' -> sb.append('\t');
        case 'r' -> sb.append('\r');
        default -> throw error("unsupported escape '\\" + next + "'");
      }
    }
    return sb.toString();
  }

  private static boolean isBareKey(String key) {
    if (key.isEmpty()) {
      return false;
    }
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      boolean ok = Character.isLetterOrDigit(c) && c < 128 || c == '_' || c == '-';
      if (!ok) {
        return false;
      }
    }
    return true;
  }

  private TomlException error(String message) {
    return new TomlException(where() + message);
  }

  private TomlException error(String message, Throwable cause) {
    return new TomlException(where() + message, cause);
  }

  private String where() {
    return String.format(Locale.ROOT, "%s:%d: ", source, lineNumber);
  }
}
