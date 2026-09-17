package io.codekoll.rules.pqc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.codekoll.api.Finding;
import io.codekoll.engine.testing.RuleTestHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

class PqcKeyMaterialRuleTest {

  private final PqcKeyMaterialRule rule = new PqcKeyMaterialRule();

  @Test
  void flagsKeyGeneratorsFactoriesAndParameters() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.security.*;
        class P1 {
          void m() throws Exception {
            KeyPairGenerator.getInstance("RSA"); // :: CK-PQC-KEY-MATERIAL
            KeyPairGenerator.getInstance("EC"); // :: CK-PQC-KEY-MATERIAL
            KeyPairGenerator.getInstance("EllipticCurve"); // :: CK-PQC-KEY-MATERIAL
            KeyPairGenerator.getInstance("DSA"); // :: CK-PQC-KEY-MATERIAL
            KeyPairGenerator.getInstance("DiffieHellman"); // :: CK-PQC-KEY-MATERIAL
            KeyPairGenerator.getInstance("X25519"); // :: CK-PQC-KEY-MATERIAL
            KeyPairGenerator.getInstance("Ed448"); // :: CK-PQC-KEY-MATERIAL
            KeyPairGenerator.getInstance("RSASSA-PSS"); // :: CK-PQC-KEY-MATERIAL
            KeyPairGenerator.getInstance("1.2.840.10045.2.1"); // :: CK-PQC-KEY-MATERIAL
            KeyFactory.getInstance("RSA"); // :: CK-PQC-KEY-MATERIAL
            AlgorithmParameters.getInstance("EC"); // :: CK-PQC-KEY-MATERIAL
            AlgorithmParameterGenerator.getInstance("DH"); // :: CK-PQC-KEY-MATERIAL
          }
        }
        """);
  }

  @Test
  void flagsParameterSpecificationsInMethodsWithoutAFactoryCall() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import java.math.BigInteger;
        import java.security.spec.*;
        import javax.crypto.spec.DHParameterSpec;
        class P2 {
          Object curve() {
            return new ECGenParameterSpec("secp256r1"); // :: CK-PQC-KEY-MATERIAL
          }
          Object rsa() {
            return new RSAKeyGenParameterSpec(3072, RSAKeyGenParameterSpec.F4); // :: CK-PQC-KEY-MATERIAL
          }
          Object dh(BigInteger p, BigInteger g) {
            return new DHParameterSpec(p, g); // :: CK-PQC-KEY-MATERIAL
          }
          Object named() {
            return new NamedParameterSpec("X25519"); // :: CK-PQC-KEY-MATERIAL
          }
          Object constant() {
            return NamedParameterSpec.ED25519; // :: CK-PQC-KEY-MATERIAL
          }
        }
        """);
  }

  @Test
  void ignoresPostQuantumAndNonAsymmetricNames() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.security.*;
        import java.security.spec.NamedParameterSpec;
        class N1 {
          void m() throws Exception {
            KeyPairGenerator.getInstance("ML-KEM");
            KeyPairGenerator.getInstance("ML-DSA-87");
            KeyFactory.getInstance("HSS/LMS");
            AlgorithmParameters.getInstance("OAEP");
            AlgorithmParameters.getInstance("GCM");
            Object spec = NamedParameterSpec.ML_KEM_768;
          }
        }
        """);
  }

  @Test
  void doesNotReportTheParameterSpecOfAKeyAlreadyReported() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import java.security.KeyPairGenerator;
        import java.security.spec.ECGenParameterSpec;
        class N2 {
          void m() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC"); // :: CK-PQC-KEY-MATERIAL
            generator.initialize(new ECGenParameterSpec("secp256r1"));
          }
        }
        """);
  }

  @Test
  void exemptsTestSources() {
    RuleTestHarness.assertFixture(rule, "src.test.java.N3", """
        import java.security.KeyPairGenerator;
        class N3 {
          void m() throws Exception {
            KeyPairGenerator.getInstance("RSA");
          }
        }
        """);
  }

  @Test
  void messageNamesTheRoleTheFamilyImplies() {
    List<String> messages = RuleTestHarness.run(rule, "M1", """
        import java.security.KeyPairGenerator;
        class M1 {
          void m() throws Exception {
            KeyPairGenerator.getInstance("X25519");
            KeyPairGenerator.getInstance("EC");
          }
        }
        """).findings().stream().map(Finding::message).toList();
    assertEquals(List.of(
        PqcGuidance.keyMaterial(List.of("X25519"), Role.KEY_ESTABLISHMENT, true),
        PqcGuidance.keyMaterial(List.of("EC"), Role.KEY_MATERIAL, true)), messages);
  }
}
