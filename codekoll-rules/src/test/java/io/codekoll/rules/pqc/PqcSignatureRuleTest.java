package io.codekoll.rules.pqc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class PqcSignatureRuleTest {

  private final PqcSignatureRule rule = new PqcSignatureRule();

  @Test
  void flagsSignatureNamesAliasesAndOids() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.security.Signature;
        class P1 {
          private static final String ALG = "SHA256withRSA";
          void m() throws Exception {
            Signature.getInstance(ALG); // :: CK-PQC-SIGNATURE
            Signature.getInstance("SHA256withECDSA"); // :: CK-PQC-SIGNATURE
            Signature.getInstance("SHA3-512withECDSAinP1363Format"); // :: CK-PQC-SIGNATURE
            Signature.getInstance("NONEwithDSA"); // :: CK-PQC-SIGNATURE
            Signature.getInstance("DSS"); // :: CK-PQC-SIGNATURE
            Signature.getInstance("RawDSA"); // :: CK-PQC-SIGNATURE
            Signature.getInstance("RSASSA-PSS"); // :: CK-PQC-SIGNATURE
            Signature.getInstance("PSS"); // :: CK-PQC-SIGNATURE
            Signature.getInstance("Ed25519"); // :: CK-PQC-SIGNATURE
            Signature.getInstance("EdDSA"); // :: CK-PQC-SIGNATURE
            Signature.getInstance("MD5andSHA1withRSA"); // :: CK-PQC-SIGNATURE
            Signature.getInstance("1.2.840.113549.1.1.11"); // :: CK-PQC-SIGNATURE
            Signature.getInstance("OID.1.2.840.10045.4.3.2"); // :: CK-PQC-SIGNATURE
          }
        }
        """);
  }

  @Test
  void flagsXmlSignatureMethodsAndTlsSignatureSchemes() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import javax.net.ssl.SSLParameters;
        import javax.xml.crypto.dsig.SignatureMethod;
        import javax.xml.crypto.dsig.XMLSignatureFactory;
        class P2 {
          void m(XMLSignatureFactory xml, SSLParameters params) throws Exception {
            xml.newSignatureMethod(SignatureMethod.RSA_SHA256, null); // :: CK-PQC-SIGNATURE
            xml.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256", null); // :: CK-PQC-SIGNATURE
            params.setSignatureSchemes(new String[] {"ecdsa_secp256r1_sha256", "ed25519"}); // :: CK-PQC-SIGNATURE
            System.setProperty("jdk.tls.client.SignatureSchemes", "rsa_pss_rsae_sha256"); // :: CK-PQC-SIGNATURE
          }
        }
        """);
  }

  @Test
  void ignoresPostQuantumAndMacNames() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.security.Signature;
        import javax.xml.crypto.dsig.SignatureMethod;
        import javax.xml.crypto.dsig.XMLSignatureFactory;
        class N1 {
          void m(XMLSignatureFactory xml, String dynamic) throws Exception {
            Signature.getInstance("ML-DSA");
            Signature.getInstance("ML-DSA-65");
            Signature.getInstance("HSS/LMS");
            Signature.getInstance("SLH-DSA-SHA2-128s");
            Signature.getInstance(dynamic);
            xml.newSignatureMethod(SignatureMethod.HMAC_SHA256, null);
          }
        }
        """);
  }

  @Test
  void guidanceFollowsTheTargetRelease() {
    String source = """
        import java.security.Signature;
        class R1 {
          void m() throws Exception {
            Signature.getInstance("Ed25519");
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
  void exemptsTestSources() {
    RuleTestHarness.assertFixture(rule, "src.test.java.N2", """
        import java.security.Signature;
        class N2 {
          void m() throws Exception {
            Signature.getInstance("SHA256withRSA");
          }
        }
        """);
  }
}
