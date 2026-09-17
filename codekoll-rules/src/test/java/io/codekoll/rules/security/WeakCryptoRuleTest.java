package io.codekoll.rules.security;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class WeakCryptoRuleTest {

  private final WeakCryptoRule rule = new WeakCryptoRule();

  @Test
  void flagsMd5Literal() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.security.MessageDigest;
        import java.security.NoSuchAlgorithmException;
        class P1 {
          void m() throws NoSuchAlgorithmException {
            MessageDigest.getInstance("MD5"); // :: CK-CRYPTO-WEAK
          }
        }
        """);
  }

  @Test
  void flagsSha1ViaConstant() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import java.security.MessageDigest;
        import java.security.NoSuchAlgorithmException;
        class P2 {
          private static final String ALG = "SHA-1";
          void m() throws NoSuchAlgorithmException {
            MessageDigest.getInstance(ALG); // :: CK-CRYPTO-WEAK
          }
        }
        """);
  }

  @Test
  void flagsDesCipherTransformation() {
    RuleTestHarness.assertFixture(rule, "P3", """
        import javax.crypto.Cipher;
        class P3 {
          void m() throws Exception {
            Cipher.getInstance("DES/CBC/PKCS5Padding"); // :: CK-CRYPTO-WEAK
          }
        }
        """);
  }

  @Test
  void flagsEcbMode() {
    RuleTestHarness.assertFixture(rule, "P4", """
        import javax.crypto.Cipher;
        class P4 {
          void m() throws Exception {
            Cipher.getInstance("AES/ECB/PKCS5Padding"); // :: CK-CRYPTO-WEAK
          }
        }
        """);
  }

  @Test
  void allowsSha256AndGcm() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.security.MessageDigest;
        import javax.crypto.Cipher;
        class N1 {
          void m() throws Exception {
            MessageDigest.getInstance("SHA-256");
            Cipher.getInstance("AES/GCM/NoPadding");
          }
        }
        """);
  }

  @Test
  void allowsNonConstantArgument() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import java.security.MessageDigest;
        class N2 {
          void m(String algorithm) throws Exception {
            MessageDigest.getInstance(algorithm);
          }
        }
        """);
  }

  /**
   * RSA has no block-cipher mode: the JCA spells its transformations RSA/ECB/<padding>, where ECB
   * means "one block" and not the mode this rule warns about. Flagging it reported an error, and
   * failed default builds, on the standard way to request OAEP.
   */
  @Test
  void doesNotFlagRsaTransformationsNamingEcb() {
    RuleTestHarness.assertFixture(rule, "N3", """
        import javax.crypto.Cipher;
        class N3 {
          void m() throws Exception {
            Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            Cipher.getInstance("RSA/ECB/PKCS1Padding");
            Cipher.getInstance("rsa/ecb/oaepwithsha-256andmgf1padding");
          }
        }
        """);
  }

  @Test
  void stillFlagsEcbForBlockCiphers() {
    RuleTestHarness.assertFixture(rule, "P9", """
        import javax.crypto.Cipher;
        class P9 {
          void m() throws Exception {
            Cipher.getInstance("AES/ECB/PKCS5Padding"); // :: CK-CRYPTO-WEAK
          }
        }
        """);
  }
}
