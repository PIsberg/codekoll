package io.codekoll.rules.pqc;

import io.codekoll.rules.pqc.Classification.NotAsymmetric;
import io.codekoll.rules.pqc.Classification.PostQuantum;
import io.codekoll.rules.pqc.Classification.Vulnerable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Classifies algorithm names by request type. Named entries come from {@code pqc-catalog.tsv},
 * generated from the JDK provider registry; symmetric and post-quantum families are recognised by
 * prefix so that new modes and parameter sets of known-safe families need no entry. A name that
 * is neither is unclassified, which {@code PqcCatalogCompletenessTest} turns into a build failure
 * for every name the running JDK exposes.
 */
final class PqcCatalog {

  private static final String RESOURCE = "pqc-catalog.tsv";

  private static final Set<RequestType> PROVIDER_TYPES = EnumSet.of(RequestType.SIGNATURE,
      RequestType.KEY_AGREEMENT, RequestType.KEM, RequestType.CIPHER, RequestType.KEY_PAIR_GENERATOR,
      RequestType.KEY_FACTORY, RequestType.ALGORITHM_PARAMETERS,
      RequestType.ALGORITHM_PARAMETER_GENERATOR);

  private static final List<String> NOT_ASYMMETRIC_PREFIXES = List.of("AES", "ARCFOUR", "BLOWFISH",
      "CHACHA20", "DES", "GCM", "OAEP", "PBE", "RC2", "RC4", "TRIPLEDES");

  private static final Map<String, Family> POST_QUANTUM_PREFIXES = Map.of("ML-KEM", Family.ML_KEM,
      "ML-DSA", Family.ML_DSA, "SLH-DSA", Family.SLH_DSA, "HSS/LMS", Family.HSS_LMS);

  private static final Map<String, Family> SUITE_KEY_EXCHANGE = Map.of("RSA", Family.RSA,
      "DH", Family.DH, "DHE", Family.DH, "ECDH", Family.EC, "ECDHE", Family.EC);

  private static final Map<RequestType, Map<String, Classification>> TABLE = load();

  private PqcCatalog() {}

  /** The verdict for {@code name} requested through {@code type}, or empty when unclassified. */
  static Optional<Classification> classify(RequestType type, String name) {
    if (type == RequestType.TLS_CIPHER_SUITE) {
      return Optional.of(classifySuite(name));
    }
    String key = key(type, name);
    Classification entry = TABLE.getOrDefault(type, Map.of()).get(key);
    if (entry != null) {
      return Optional.of(entry);
    }
    if (PROVIDER_TYPES.contains(type)) {
      for (Map.Entry<String, Family> prefix : POST_QUANTUM_PREFIXES.entrySet()) {
        if (key.startsWith(prefix.getKey())) {
          return Optional.of(new PostQuantum(prefix.getValue()));
        }
      }
      for (String prefix : NOT_ASYMMETRIC_PREFIXES) {
        if (key.startsWith(prefix)) {
          return Optional.of(new NotAsymmetric());
        }
      }
    }
    return Optional.empty();
  }

  /** Upper-cased names with an explicit entry for {@code type}. */
  static Set<String> names(RequestType type) {
    return Collections.unmodifiableSet(TABLE.getOrDefault(type, Map.of()).keySet());
  }

  /** Splits a comma-separated property value such as {@code jdk.tls.namedGroups}. */
  static List<String> splitList(String value) {
    return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  private static String key(RequestType type, String name) {
    String trimmed = name.trim();
    if (type == RequestType.CIPHER) {
      int slash = trimmed.indexOf('/');
      trimmed = slash < 0 ? trimmed : trimmed.substring(0, slash).trim();
    }
    return trimmed.toUpperCase(Locale.ROOT);
  }

  /**
   * TLS 1.2 and older suites name their key exchange between the {@code TLS_}/{@code SSL_} prefix and
   * {@code _WITH_}; TLS 1.3 suites have no {@code _WITH_} and leave key exchange to named groups.
   */
  private static Classification classifySuite(String suite) {
    String upper = suite.trim().toUpperCase(Locale.ROOT);
    int with = upper.indexOf("_WITH_");
    if (with < 0 || !(upper.startsWith("TLS_") || upper.startsWith("SSL_"))) {
      return new NotAsymmetric();
    }
    String exchange = upper.substring(4, with).split("_", 2)[0];
    Family family = SUITE_KEY_EXCHANGE.get(exchange);
    return family == null
        ? new NotAsymmetric()
        : new Vulnerable(family, Role.KEY_ESTABLISHMENT, suite.trim());
  }

  private static Map<RequestType, Map<String, Classification>> load() {
    Map<RequestType, Map<String, Classification>> table = new EnumMap<>(RequestType.class);
    List<String> lines;
    try (InputStream in = Objects.requireNonNull(PqcCatalog.class.getResourceAsStream(RESOURCE),
            "missing resource " + RESOURCE);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      lines = reader.lines().toList();
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + RESOURCE, e);
    }
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (!line.isBlank() && !line.startsWith("#")) {
        addRow(table, line, i + 1);
      }
    }
    Map<RequestType, Map<String, Classification>> frozen = new EnumMap<>(RequestType.class);
    table.forEach((type, entries) -> frozen.put(type, Map.copyOf(entries)));
    return Collections.unmodifiableMap(frozen);
  }

  private static void addRow(Map<RequestType, Map<String, Classification>> table, String line,
      int lineNumber) {
    String[] columns = line.split("\t");
    if (columns.length != 5) {
      throw new IllegalStateException(RESOURCE + ":" + lineNumber + " needs 5 tab-separated columns");
    }
    RequestType type = RequestType.valueOf(columns[0]);
    String[] names = columns[4].split(",");
    Classification classification = switch (columns[1]) {
      case "VULNERABLE" -> new Vulnerable(Family.valueOf(columns[2]), Role.valueOf(columns[3]), names[0]);
      case "POST_QUANTUM" -> new PostQuantum(Family.valueOf(columns[2]));
      case "NOT_ASYMMETRIC" -> new NotAsymmetric();
      default -> throw new IllegalStateException(RESOURCE + ":" + lineNumber + " unknown class "
          + columns[1]);
    };
    Map<String, Classification> entries = table.computeIfAbsent(type, t -> new HashMap<>());
    for (String name : names) {
      if (entries.putIfAbsent(key(type, name), classification) != null) {
        throw new IllegalStateException(RESOURCE + ":" + lineNumber + " duplicates " + type + " " + name);
      }
    }
  }
}
