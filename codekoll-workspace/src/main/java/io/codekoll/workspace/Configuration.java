package io.codekoll.workspace;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * The merged effective configuration, with the provenance of every value (CLI-SPEC §10).
 *
 * <p>Provenance is carried from the start rather than added later because it is the answer to the
 * only interesting question a configuration bug ever asks: not "what is the setting" but "which
 * file set it". {@code --print-config} prints exactly this.
 */
public final class Configuration {

  /**
   * One effective value and where it came from.
   *
   * @param value a {@code String}, {@code Integer}, {@code Boolean}, or {@code List<String>}
   * @param origin human-readable source, e.g. {@code repo config (codekoll.toml:4)}
   */
  public record Value(Object value, String origin) {}

  private final Map<String, Value> values;

  Configuration(Map<String, Value> values) {
    this.values = new LinkedHashMap<>(values);
  }

  /** The empty configuration: everything falls back to its built-in default. */
  public static Configuration empty() {
    return new Configuration(Map.of());
  }

  /**
   * Every effective value, in the order the keys were set, for {@code --print-config}.
   *
   * @return an unmodifiable, order-preserving view ({@code Map.copyOf} would lose the order,
   *     which is the one property this map is printed for)
   */
  public Map<String, Value> values() {
    return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  /** True when nothing at all was configured. */
  public boolean isEmpty() {
    return values.isEmpty();
  }

  /**
   * @param key dotted key, e.g. {@code report.format}
   * @return the origin of that value, or empty when it was never set
   */
  public Optional<String> originOf(String key) {
    Value value = values.get(key);
    return value == null ? Optional.empty() : Optional.of(value.origin());
  }

  /**
   * @param key dotted key
   * @return the string value, or empty when unset
   * @throws ConfigException if the value is present but not a string
   */
  public Optional<String> string(String key) {
    Object raw = raw(key);
    if (raw == null) {
      return Optional.empty();
    }
    if (!(raw instanceof String text)) {
      throw typeError(key, "a string");
    }
    return Optional.of(text);
  }

  /**
   * @param key dotted key
   * @return the string list, empty when unset
   * @throws ConfigException if the value is present but not a list of strings
   */
  public List<String> strings(String key) {
    Object raw = raw(key);
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> list)) {
      throw typeError(key, "an array of strings");
    }
    return list.stream()
        .map(item -> {
          if (!(item instanceof String text)) {
            throw typeError(key, "an array of strings");
          }
          return text;
        })
        .toList();
  }

  /**
   * @param key dotted key
   * @return the integer value, or empty when unset
   * @throws ConfigException if the value is present but not an integer
   */
  public OptionalInt integer(String key) {
    Object raw = raw(key);
    if (raw == null) {
      return OptionalInt.empty();
    }
    if (!(raw instanceof Integer number)) {
      throw typeError(key, "an integer");
    }
    return OptionalInt.of(number);
  }

  /**
   * @param key dotted key
   * @return the boolean value, or empty when unset
   * @throws ConfigException if the value is present but not a boolean
   */
  public Optional<Boolean> bool(String key) {
    Object raw = raw(key);
    if (raw == null) {
      return Optional.empty();
    }
    if (!(raw instanceof Boolean flag)) {
      throw typeError(key, "true or false");
    }
    return Optional.of(flag);
  }

  /**
   * Returns every key of a free-form table, unquoted, in document order — used for
   * {@code [severity]}, whose keys are rule ids rather than a fixed schema.
   *
   * @param table table name, e.g. {@code severity}
   * @return map of key to string value
   * @throws ConfigException if any value in the table is not a string
   */
  public Map<String, String> table(String table) {
    Map<String, String> result = new LinkedHashMap<>();
    String prefix = table + ".";
    for (Map.Entry<String, Value> entry : values.entrySet()) {
      if (!entry.getKey().startsWith(prefix)) {
        continue;
      }
      String key = unquote(entry.getKey().substring(prefix.length()));
      Object raw = entry.getValue().value();
      if (!(raw instanceof String text)) {
        throw typeError(entry.getKey(), "a string");
      }
      result.put(key, text.toLowerCase(Locale.ROOT));
    }
    return result;
  }

  private @Nullable Object raw(String key) {
    Value value = values.get(key);
    return value == null ? null : value.value();
  }

  private ConfigException typeError(String key, String expected) {
    Value value = values.get(key);
    String origin = value == null ? "" : " (" + value.origin() + ")";
    return new ConfigException(key + origin + " must be " + expected);
  }

  private static String unquote(String key) {
    return key.length() >= 2 && key.charAt(0) == '"' && key.endsWith("\"")
        ? key.substring(1, key.length() - 1)
        : key;
  }
}
