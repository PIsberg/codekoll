package io.codekoll.rules.pqc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.api.Finding;
import io.codekoll.engine.testing.RuleTestHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

class PqcKeyExchangeRuleTest {

  private final PqcKeyExchangeRule rule = new PqcKeyExchangeRule();

  @Test
  void flagsKeyAgreementNamesAliasesAndOids() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import javax.crypto.KeyAgreement;
        class P1 {
          void m() throws Exception {
            KeyAgreement.getInstance("DiffieHellman"); // :: CK-PQC-KEY-EXCHANGE
            KeyAgreement.getInstance("DH"); // :: CK-PQC-KEY-EXCHANGE
            KeyAgreement.getInstance("ECDH"); // :: CK-PQC-KEY-EXCHANGE
            KeyAgreement.getInstance("XDH"); // :: CK-PQC-KEY-EXCHANGE
            KeyAgreement.getInstance("X25519"); // :: CK-PQC-KEY-EXCHANGE
            KeyAgreement.getInstance("X448"); // :: CK-PQC-KEY-EXCHANGE
            KeyAgreement.getInstance("1.3.101.110"); // :: CK-PQC-KEY-EXCHANGE
          }
        }
        """);
  }

  @Test
  void flagsClassicalKemRsaCiphersAndHpke() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import javax.crypto.Cipher;
        import javax.crypto.KEM;
        class P2 {
          private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
          void m() throws Exception {
            KEM.getInstance("DHKEM"); // :: CK-PQC-KEY-EXCHANGE
            Cipher.getInstance("RSA"); // :: CK-PQC-KEY-EXCHANGE
            Cipher.getInstance("RSA/ECB/PKCS1Padding"); // :: CK-PQC-KEY-EXCHANGE
            Cipher.getInstance("rsa/ecb/oaepwithsha-256andmgf1padding"); // :: CK-PQC-KEY-EXCHANGE
            Cipher.getInstance("HPKE"); // :: CK-PQC-KEY-EXCHANGE
            Cipher.getInstance("RSA", "SunJCE"); // :: CK-PQC-KEY-EXCHANGE
            Cipher.getInstance(TRANSFORMATION); // :: CK-PQC-KEY-EXCHANGE
          }
        }
        """);
  }

  @Test
  void flagsTlsGroupsSuitesAndPropertiesOncePerSite() {
    RuleTestHarness.assertFixture(rule, "P3", """
        import java.security.Security;
        import javax.net.ssl.SSLParameters;
        import javax.net.ssl.SSLSocket;
        class P3 {
          void m(SSLParameters params, SSLSocket socket) {
            params.setNamedGroups(new String[] {"x25519", "secp256r1"}); // :: CK-PQC-KEY-EXCHANGE
            socket.setEnabledCipherSuites(new String[] {"TLS_AES_256_GCM_SHA384", // :: CK-PQC-KEY-EXCHANGE
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"});
            System.setProperty("jdk.tls.namedGroups", "x25519,ffdhe2048"); // :: CK-PQC-KEY-EXCHANGE
            Security.setProperty("jdk.tls.namedGroups", "secp384r1"); // :: CK-PQC-KEY-EXCHANGE
          }
        }
        """);
  }

  /** Suites can also be pinned by system property; the JDK reads three (verified in JDK 26). */
  @Test
  void flagsCipherSuitesSetThroughSystemProperties() {
    RuleTestHarness.assertFixture(rule, "P4", """
        import java.security.Security;
        class P4 {
          void m() {
            System.setProperty("jdk.tls.client.cipherSuites", // :: CK-PQC-KEY-EXCHANGE
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384");
            Security.setProperty("jdk.tls.server.cipherSuites", // :: CK-PQC-KEY-EXCHANGE
                "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256");
            System.setProperty("https.cipherSuites", // :: CK-PQC-KEY-EXCHANGE
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA");
            System.setProperty("jdk.tls.client.cipherSuites", "TLS_AES_128_GCM_SHA256");
          }
        }
        """);
  }

  /** JOSE key-management algorithms: RSA key transport and ECDH-ES are quantum-vulnerable. */
  @Test
  void flagsJoseKeyManagementConstants() {
    RuleTestHarness.assertFixture(rule, "P5", """
        class P5 {
          static final class JWEAlgorithm {
            static final String RSA_OAEP_256 = "RSA-OAEP-256";
            static final String ECDH_ES_A128KW = "ECDH-ES+A128KW";
            static final String A128KW = "A128KW";
          }
          Object[] m() {
            return new Object[] {
              JWEAlgorithm.RSA_OAEP_256, // :: CK-PQC-KEY-EXCHANGE
              JWEAlgorithm.ECDH_ES_A128KW, // :: CK-PQC-KEY-EXCHANGE
              JWEAlgorithm.A128KW,
            };
          }
        }
        """);
  }

  @Test
  void ignoresPostQuantumSymmetricAndPasswordBasedNames() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import javax.crypto.Cipher;
        import javax.crypto.KEM;
        class N1 {
          void m() throws Exception {
            KEM.getInstance("ML-KEM");
            KEM.getInstance("ML-KEM-768");
            Cipher.getInstance("AES/GCM/NoPadding");
            Cipher.getInstance("PBEWithHmacSHA256AndAES_256");
            Cipher.getInstance("ChaCha20-Poly1305");
          }
        }
        """);
  }

  @Test
  void ignoresNamesNotKnownAtCompileTime() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import javax.crypto.KeyAgreement;
        class N2 {
          void m(String algorithm) throws Exception {
            KeyAgreement.getInstance(algorithm);
          }
        }
        """);
  }

  @Test
  void exemptsHybridKeyEstablishmentWithMlKemInTheSameMethod() {
    RuleTestHarness.assertFixture(rule, "N3", """
        import javax.crypto.KEM;
        import javax.crypto.KeyAgreement;
        class N3 {
          void hybrid() throws Exception {
            KeyAgreement.getInstance("X25519");
            KEM.getInstance("ML-KEM-768");
          }
          void classicalOnly() throws Exception {
            KeyAgreement.getInstance("X25519"); // :: CK-PQC-KEY-EXCHANGE
          }
        }
        """);
  }

  @Test
  void ignoresTls13SuitesAndUnrelatedProperties() {
    RuleTestHarness.assertFixture(rule, "N4", """
        import javax.net.ssl.SSLSocket;
        class N4 {
          void m(SSLSocket socket) {
            socket.setEnabledCipherSuites(new String[] {"TLS_AES_256_GCM_SHA384"});
            System.setProperty("jdk.tls.other", "x25519");
          }
        }
        """);
  }

  @Test
  void exemptsTestSources() {
    RuleTestHarness.assertFixture(rule, "src.test.java.N5", """
        import javax.crypto.KeyAgreement;
        class N5 {
          void m() throws Exception {
            KeyAgreement.getInstance("ECDH");
          }
        }
        """);
  }

  @Test
  void guidanceFollowsTheTargetRelease() {
    String source = """
        import javax.crypto.KeyAgreement;
        class R1 {
          void m() throws Exception {
            KeyAgreement.getInstance("ECDH");
          }
        }
        """;
    String release21 = RuleTestHarness.run(rule, "R1", source, 21).findings().get(0).message();
    String release25 = RuleTestHarness.run(rule, "R1", source, 25).findings().get(0).message();
    assertTrue(release21.contains(
        "not built into this project's Java release: use Bouncy Castle PQC or target Java 24+"), release21);
    assertTrue(release25.contains("built into this project's Java platform"), release25);
  }

  @Test
  void messageNamesTheAlgorithmAndTheBuiltInReplacement() {
    List<Finding> findings = RuleTestHarness.run(rule, "M1", """
        import javax.crypto.KeyAgreement;
        class M1 {
          void m() throws Exception {
            KeyAgreement.getInstance("ECDH");
          }
        }
        """).findings();
    assertEquals(List.of(PqcGuidance.keyEstablishment(List.of("ECDH"), true)),
        findings.stream().map(Finding::message).toList());
  }
}
