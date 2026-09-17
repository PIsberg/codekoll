package io.codekoll.rules.pqc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.Provider;
import java.security.Security;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Test;

/**
 * FR-016: every algorithm name, alias and OID the running JDK exposes for a covered request type
 * must be classified, so that a JDK upgrade adding an algorithm fails the build instead of silently
 * narrowing coverage. All gaps are reported in one run.
 */
class PqcCatalogCompletenessTest {

  private static final Map<String, RequestType> PROVIDER_TYPES = Map.of(
      "Signature", RequestType.SIGNATURE,
      "KeyAgreement", RequestType.KEY_AGREEMENT,
      "KEM", RequestType.KEM,
      "Cipher", RequestType.CIPHER,
      "KeyPairGenerator", RequestType.KEY_PAIR_GENERATOR,
      "KeyFactory", RequestType.KEY_FACTORY,
      "AlgorithmParameters", RequestType.ALGORITHM_PARAMETERS,
      "AlgorithmParameterGenerator", RequestType.ALGORITHM_PARAMETER_GENERATOR);

  private static final String ALIAS_PREFIX = "Alg.Alias.";

  private final TreeSet<String> gaps = new TreeSet<>();

  @Test
  void everyNameTheRunningJdkExposesIsClassified() throws Exception {
    for (Provider provider : Security.getProviders()) {
      for (Provider.Service service : provider.getServices()) {
        RequestType type = PROVIDER_TYPES.get(service.getType());
        if (type != null && PqcCatalog.classify(type, service.getAlgorithm()).isEmpty()) {
          gaps.add(service.getType() + "  " + service.getAlgorithm() + "  (provider " + provider.getName()
              + ")");
        }
      }
      for (Object key : provider.keySet()) {
        checkAlias(provider, String.valueOf(key));
      }
    }

    SSLParameters tls = SSLContext.getDefault().getSupportedSSLParameters();
    for (String suite : tls.getCipherSuites()) {
      requireClassified(RequestType.TLS_CIPHER_SUITE, suite, "TLS cipher suite");
    }
    String[] groups = tls.getNamedGroups();
    assertFalse(groups == null || groups.length == 0, "the JDK reported no TLS named groups to check");
    for (String group : groups) {
      requireClassified(RequestType.TLS_NAMED_GROUP, group, "TLS named group");
    }
    String[] schemes = tls.getSignatureSchemes();
    if (schemes == null) {
      // A null list means "provider default": nothing to enumerate, so the catalog's own list must
      // not be what makes this pass vacuously.
      assertFalse(PqcCatalog.names(RequestType.TLS_SIGNATURE_SCHEME).isEmpty());
    } else {
      for (String scheme : schemes) {
        requireClassified(RequestType.TLS_SIGNATURE_SCHEME, scheme, "TLS signature scheme");
      }
    }

    Class<?> signatureMethod = Class.forName("javax.xml.crypto.dsig.SignatureMethod");
    for (Field field : signatureMethod.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
        requireClassified(RequestType.XML_SIGNATURE_METHOD, (String) field.get(null),
            "XML SignatureMethod." + field.getName());
      }
    }

    assertTrue(gaps.isEmpty(), "Unclassified by the pqc catalog on " + System.getProperty("java.vendor")
        + " " + System.getProperty("java.version") + " (add them to pqc-catalog.tsv):\n  "
        + String.join("\n  ", gaps));
  }

  /**
   * An alias must classify exactly like its target. The one allowance: an alias of a not-asymmetric
   * algorithm (for example an AES OID) may stay unclassified, because the rules never report
   * unclassified names and a symmetric alias is not a finding either way.
   */
  private void checkAlias(Provider provider, String key) {
    if (!key.startsWith(ALIAS_PREFIX)) {
      return;
    }
    String rest = key.substring(ALIAS_PREFIX.length());
    int dot = rest.indexOf('.');
    RequestType type = dot < 0 ? null : PROVIDER_TYPES.get(rest.substring(0, dot));
    if (type == null) {
      return;
    }
    String alias = rest.substring(dot + 1);
    String target = provider.getProperty(key);
    Optional<Classification> aliasClass = PqcCatalog.classify(type, alias);
    Optional<Classification> targetClass = PqcCatalog.classify(type, target);
    boolean symmetricAlias = aliasClass.isEmpty()
        && targetClass.filter(c -> c instanceof Classification.NotAsymmetric).isPresent();
    if (!symmetricAlias && (aliasClass.isEmpty() || !aliasClass.equals(targetClass))) {
      gaps.add(rest.substring(0, dot) + "  " + alias + "  (alias of " + target + ", provider "
          + provider.getName() + ")");
    }
  }

  private void requireClassified(RequestType type, String name, String what) {
    if (PqcCatalog.classify(type, name).isEmpty()) {
      gaps.add(what + "  " + name);
    }
  }
}
