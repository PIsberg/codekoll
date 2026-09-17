package io.codekoll.rules.pqc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.rules.pqc.Classification.NotAsymmetric;
import io.codekoll.rules.pqc.Classification.PostQuantum;
import io.codekoll.rules.pqc.Classification.Vulnerable;
import java.util.List;
import org.junit.jupiter.api.Test;

class PqcCatalogTest {

  private static Classification classify(RequestType type, String name) {
    return PqcCatalog.classify(type, name)
        .orElseThrow(() -> new AssertionError(type + " " + name + " is unclassified"));
  }

  private static Vulnerable vulnerable(RequestType type, String name) {
    return assertInstanceOf(Vulnerable.class, classify(type, name), type + " " + name);
  }

  @Test
  void lookupIgnoresCase() {
    assertEquals("SHA256withECDSA",
        vulnerable(RequestType.SIGNATURE, "sha256withecdsa").canonicalName());
  }

  @Test
  void aliasesAndOidsResolveToTheirCanonicalEntry() {
    assertEquals("SHA1withDSA", vulnerable(RequestType.SIGNATURE, "DSS").canonicalName());
    assertEquals("NONEwithDSA", vulnerable(RequestType.SIGNATURE, "RawDSA").canonicalName());
    assertEquals("SHA1withDSA", vulnerable(RequestType.SIGNATURE, "SHAwithDSA").canonicalName());
    assertEquals("RSASSA-PSS", vulnerable(RequestType.SIGNATURE, "PSS").canonicalName());
    assertEquals("SHA1withRSA", vulnerable(RequestType.SIGNATURE, "1.3.14.3.2.29").canonicalName());
    assertEquals("SHA256withECDSA",
        vulnerable(RequestType.SIGNATURE, "OID.1.2.840.10045.4.3.2").canonicalName());
    assertEquals("EC", vulnerable(RequestType.KEY_PAIR_GENERATOR, "EllipticCurve").canonicalName());
    assertEquals("DiffieHellman", vulnerable(RequestType.KEY_AGREEMENT, "DH").canonicalName());
    assertEquals("X25519", vulnerable(RequestType.KEY_AGREEMENT, "1.3.101.110").canonicalName());
  }

  @Test
  void cipherLookupUsesTheFirstTransformationSegment() {
    Vulnerable rsa = vulnerable(RequestType.CIPHER, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
    assertEquals(Family.RSA, rsa.family());
    assertEquals(Role.KEY_ESTABLISHMENT, rsa.role());
  }

  @Test
  void classicalKemAndHpkeAreKeyEstablishment() {
    assertEquals(Role.KEY_ESTABLISHMENT, vulnerable(RequestType.KEM, "DHKEM").role());
    assertEquals(Role.KEY_ESTABLISHMENT, vulnerable(RequestType.CIPHER, "HPKE").role());
  }

  @Test
  void postQuantumNamesAndOidsAreSafe() {
    assertInstanceOf(PostQuantum.class, classify(RequestType.KEM, "ML-KEM-768"));
    assertInstanceOf(PostQuantum.class, classify(RequestType.KEM, "2.16.840.1.101.3.4.4.2"));
    assertInstanceOf(PostQuantum.class, classify(RequestType.SIGNATURE, "ML-DSA-65"));
    assertInstanceOf(PostQuantum.class, classify(RequestType.SIGNATURE, "HSS/LMS"));
    assertInstanceOf(PostQuantum.class, classify(RequestType.SIGNATURE, "SLH-DSA-SHA2-128s"));
  }

  @Test
  void symmetricAndPasswordBasedNamesAreNotAsymmetric() {
    assertInstanceOf(NotAsymmetric.class, classify(RequestType.CIPHER, "AES/GCM/NoPadding"));
    assertInstanceOf(NotAsymmetric.class, classify(RequestType.CIPHER, "PBEWithHmacSHA256AndAES_256"));
    assertInstanceOf(NotAsymmetric.class, classify(RequestType.CIPHER, "ChaCha20-Poly1305"));
    assertInstanceOf(NotAsymmetric.class, classify(RequestType.ALGORITHM_PARAMETERS, "OAEP"));
  }

  @Test
  void keyMaterialRoleFollowsTheFamily() {
    assertEquals(Role.KEY_ESTABLISHMENT, vulnerable(RequestType.KEY_PAIR_GENERATOR, "X25519").role());
    assertEquals(Role.SIGNATURE, vulnerable(RequestType.KEY_PAIR_GENERATOR, "Ed25519").role());
    assertEquals(Role.SIGNATURE, vulnerable(RequestType.KEY_FACTORY, "RSASSA-PSS").role());
    assertEquals(Role.KEY_MATERIAL, vulnerable(RequestType.KEY_PAIR_GENERATOR, "EC").role());
    assertEquals(Role.KEY_MATERIAL, vulnerable(RequestType.KEY_FACTORY, "RSA").role());
  }

  @Test
  void tlsSuitesClassifyByKeyExchangeToken() {
    for (String suite : List.of("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "TLS_RSA_WITH_AES_128_CBC_SHA",
        "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA")) {
      assertEquals(Role.KEY_ESTABLISHMENT, vulnerable(RequestType.TLS_CIPHER_SUITE, suite).role(), suite);
    }
    for (String suite : List.of("TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256",
        "TLS_EMPTY_RENEGOTIATION_INFO_SCSV", "TLS_PSK_WITH_AES_128_CBC_SHA")) {
      assertInstanceOf(NotAsymmetric.class, classify(RequestType.TLS_CIPHER_SUITE, suite), suite);
    }
  }

  @Test
  void tlsGroupsAndSchemesHaveTheirRole() {
    for (String group : List.of("x25519", "secp256r1", "ffdhe2048", "X448")) {
      assertEquals(Role.KEY_ESTABLISHMENT, vulnerable(RequestType.TLS_NAMED_GROUP, group).role(), group);
    }
    for (String scheme : List.of("ecdsa_secp256r1_sha256", "rsa_pss_rsae_sha256", "ed25519", "dsa_sha1")) {
      assertEquals(Role.SIGNATURE, vulnerable(RequestType.TLS_SIGNATURE_SCHEME, scheme).role(), scheme);
    }
  }

  @Test
  void xmlSignatureMethodUrisClassify() {
    assertEquals(Family.RSA, vulnerable(RequestType.XML_SIGNATURE_METHOD,
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256").family());
    assertEquals(Family.EC, vulnerable(RequestType.XML_SIGNATURE_METHOD,
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256").family());
    assertInstanceOf(NotAsymmetric.class, classify(RequestType.XML_SIGNATURE_METHOD,
        "http://www.w3.org/2001/04/xmldsig-more#hmac-sha256"));
  }

  @Test
  void unknownNamesStayUnclassified() {
    assertTrue(PqcCatalog.classify(RequestType.SIGNATURE, "NoSuchAlgorithm").isEmpty());
  }

  @Test
  void propertyListsSplitOnCommasAndTrim() {
    assertEquals(List.of("x25519", "ffdhe2048"), PqcCatalog.splitList(" x25519 , ffdhe2048,"));
  }
}
