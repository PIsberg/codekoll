package io.codekoll.rules.pqc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.Finding;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.engine.AnalysisResult;
import io.codekoll.engine.testing.RuleTestHarness;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises the site matcher through a probe rule that reports every match it sees. */
class PqcSitesTest {

  /** Reports {@code TYPE:name|name} for each matched site whose names include a vulnerable one. */
  static final class ProbeRule extends AbstractRule {
    @Override
    public RuleId id() {
      return new RuleId("CK-PQC-PROBE");
    }

    @Override
    public RulePack pack() {
      return RulePack.PQC;
    }

    @Override
    public Severity defaultSeverity() {
      return Severity.INFO;
    }

    @Override
    public String description() {
      return "test probe";
    }

    @Override
    public String explanation() {
      return "test probe";
    }

    @Override
    public String fix() {
      return "test probe";
    }

    @Override
    protected TreePathScanner<Void, RuleContext> scanner() {
      return new TreePathScanner<>() {
        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
          report(ctx);
          return super.visitMethodInvocation(node, ctx);
        }

        @Override
        public Void visitNewClass(NewClassTree node, RuleContext ctx) {
          report(ctx);
          return super.visitNewClass(node, ctx);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, RuleContext ctx) {
          report(ctx);
          return super.visitMemberSelect(node, ctx);
        }

        private void report(RuleContext ctx) {
          PqcSites.match(getCurrentPath(), ctx)
              .filter(request -> !request.vulnerable().isEmpty())
              .ifPresent(request -> ctx.report(request.tree(),
                  request.type() + ":" + String.join("|", request.vulnerableNames())));
        }
      };
    }
  }

  private static List<String> messages(String body) {
    String source = """
        import java.security.*;
        import java.security.spec.*;
        import javax.crypto.*;
        import javax.crypto.spec.*;
        import javax.net.ssl.*;
        import javax.xml.crypto.dsig.*;
        class S {
          static final String ALG = "SHA256withRSA";
          static final String[] GROUPS = {"x448", "secp384r1"};
          void m(String dynamic, SSLParameters params, SSLSocket socket, SSLEngine engine,
              XMLSignatureFactory xml) throws Exception {
        """ + body + """
          }
        }
        """;
    AnalysisResult result = RuleTestHarness.run(new ProbeRule(), "S", source);
    assertTrue(result.skippedFiles().isEmpty(), "fixture must compile: " + result.skippedFiles());
    assertTrue(result.ruleFailures().isEmpty(), "probe crashed: " + result.ruleFailures());
    return result.findings().stream().map(Finding::message).toList();
  }

  @Test
  void factoryRequestsForEveryProviderType() {
    assertEquals(List.of(
        "SIGNATURE:SHA256withECDSA",
        "KEY_AGREEMENT:ECDH",
        "KEM:DHKEM",
        "CIPHER:RSA/ECB/PKCS1Padding",
        "KEY_PAIR_GENERATOR:EC",
        "KEY_FACTORY:RSA",
        "ALGORITHM_PARAMETERS:DSA",
        "ALGORITHM_PARAMETER_GENERATOR:DH"),
        messages("""
            Signature.getInstance("SHA256withECDSA");
            KeyAgreement.getInstance("ECDH");
            KEM.getInstance("DHKEM");
            Cipher.getInstance("RSA/ECB/PKCS1Padding");
            KeyPairGenerator.getInstance("EC");
            KeyFactory.getInstance("RSA");
            AlgorithmParameters.getInstance("DSA");
            AlgorithmParameterGenerator.getInstance("DH");
            """));
  }

  @Test
  void providerArgumentAndConstantNames() {
    assertEquals(List.of("CIPHER:RSA", "SIGNATURE:SHA256withRSA"), messages("""
        Cipher.getInstance("RSA", "BC");
        Signature.getInstance(ALG);
        """));
  }

  @Test
  void nonConstantAndSafeNamesDoNotMatch() {
    assertEquals(List.of(), messages("""
        Signature.getInstance(dynamic);
        KEM.getInstance("ML-KEM");
        Cipher.getInstance("AES/GCM/NoPadding");
        KeyPairGenerator.getInstance("ML-DSA-65");
        """));
  }

  @Test
  void parameterSpecifications() {
    assertEquals(List.of(
        "PARAMETER_SPEC:secp256r1",
        "PARAMETER_SPEC:RSA",
        "PARAMETER_SPEC:X25519",
        "PARAMETER_SPEC:Ed25519"),
        messages("""
            new ECGenParameterSpec("secp256r1");
            new RSAKeyGenParameterSpec(3072, RSAKeyGenParameterSpec.F4);
            new NamedParameterSpec("X25519");
            Object ed = NamedParameterSpec.ED25519;
            Object safe = NamedParameterSpec.ML_KEM_768;
            """));
  }

  @Test
  void tlsConfiguration() {
    assertEquals(List.of(
        "TLS_NAMED_GROUP:x25519|secp256r1",
        "TLS_NAMED_GROUP:x448|secp384r1",
        "TLS_SIGNATURE_SCHEME:ed25519",
        "TLS_CIPHER_SUITE:TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_CIPHER_SUITE:TLS_RSA_WITH_AES_128_CBC_SHA",
        "TLS_NAMED_GROUP:x25519|ffdhe2048",
        "TLS_SIGNATURE_SCHEME:rsa_pss_rsae_sha256"),
        messages("""
            params.setNamedGroups(new String[] {"x25519", "secp256r1"});
            params.setNamedGroups(GROUPS);
            params.setSignatureSchemes(new String[] {"ed25519"});
            socket.setEnabledCipherSuites(new String[] {
                "TLS_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"});
            engine.setEnabledCipherSuites(new String[] {"TLS_RSA_WITH_AES_128_CBC_SHA"});
            params.setCipherSuites(new String[] {"TLS_AES_128_GCM_SHA256"});
            System.setProperty("jdk.tls.namedGroups", "x25519,ffdhe2048");
            Security.setProperty("jdk.tls.client.SignatureSchemes", "rsa_pss_rsae_sha256");
            System.setProperty("jdk.tls.other", "x25519");
            """));
  }

  @Test
  void xmlSignatureMethods() {
    assertEquals(List.of(
        "XML_SIGNATURE_METHOD:http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
        "XML_SIGNATURE_METHOD:http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"),
        messages("""
            xml.newSignatureMethod(SignatureMethod.RSA_SHA256, null);
            xml.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256", null);
            xml.newSignatureMethod(SignatureMethod.HMAC_SHA256, null);
            """));
  }
}
