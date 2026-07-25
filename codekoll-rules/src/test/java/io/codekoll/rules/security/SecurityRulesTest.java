package io.codekoll.rules.security;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class SecurityRulesTest {

  @Test
  void hardcodedSecretFlagged() {
    RuleTestHarness.assertFixture(new HardcodedSecretRule(), "P1", """
        class P1 {
          private static final String DB_PASSWORD = "hunter2"; // :: CK-HARDCODED-SECRET
          private String apiKey;
          void m() {
            apiKey = "sk-live-abc123"; // :: CK-HARDCODED-SECRET
          }
          String use() {
            return DB_PASSWORD + apiKey;
          }
        }
        """);
  }

  @Test
  void placeholdersAndFalseFriendsAllowed() {
    RuleTestHarness.assertFixture(new HardcodedSecretRule(), "N1", """
        class N1 {
          private static final String PASSWORD_PROMPT = "Enter your password:";
          private static final String DB_PASSWORD = "${db.password}";
          private static final String TOKEN_HEADER = "X-Auth-Token";
          private final String password = System.getenv("DB_PASSWORD");
          String use() {
            return PASSWORD_PROMPT + DB_PASSWORD + TOKEN_HEADER + password;
          }
        }
        """);
  }

  @Test
  void weakTlsFlagged() {
    RuleTestHarness.assertFixture(new WeakTlsRule(), "P2", """
        import javax.net.ssl.SSLContext;
        class P2 {
          SSLContext m() throws Exception {
            return SSLContext.getInstance("SSLv3"); // :: CK-WEAK-TLS
          }
        }
        """);
  }

  @Test
  void modernTlsAllowed() {
    RuleTestHarness.assertFixture(new WeakTlsRule(), "N2", """
        import javax.net.ssl.SSLContext;
        class N2 {
          SSLContext m() throws Exception {
            return SSLContext.getInstance("TLSv1.3");
          }
        }
        """);
  }

  @Test
  void constantSeedFlagged() {
    RuleTestHarness.assertFixture(new InsecureRandomRule(), "P3", """
        import java.util.Random;
        class P3 {
          Random m() {
            return new Random(42L); // :: CK-INSECURE-RANDOM
          }
        }
        """);
  }

  @Test
  void secretFromRandomFlagged() {
    RuleTestHarness.assertFixture(new InsecureRandomRule(), "P4", """
        class P4 {
          String m() {
            long sessionToken = (long) (Math.random() * 1_000_000_000L); // :: CK-INSECURE-RANDOM
            return Long.toString(sessionToken);
          }
        }
        """);
  }

  @Test
  void secureRandomAndPlainUsesAllowed() {
    RuleTestHarness.assertFixture(new InsecureRandomRule(), "N3", """
        import java.security.SecureRandom;
        import java.util.Random;
        class N3 {
          byte[] m() {
            byte[] sessionToken = new byte[32];
            new SecureRandom().nextBytes(sessionToken);
            Random dice = new Random();
            int roll = dice.nextInt(6);
            return roll > 0 ? sessionToken : null;
          }
        }
        """);
  }

  @Test
  void plainHttpFlagged() {
    RuleTestHarness.assertFixture(new PlainHttpRule(), "P5", """
        import java.net.URI;
        class P5 {
          URI m() throws Exception {
            return new URI("http://api.payment-provider.com/charge"); // :: CK-PLAIN-HTTP
          }
        }
        """);
  }

  @Test
  void httpsAndLocalAndNamespacesAllowed() {
    RuleTestHarness.assertFixture(new PlainHttpRule(), "N4", """
        import java.net.URI;
        class N4 {
          URI[] m() throws Exception {
            return new URI[] {
              new URI("https://api.example.com"),
              new URI("http://localhost:8080/health"),
              new URI("http://www.w3.org/2001/XMLSchema"),
            };
          }
        }
        """);
  }

  @Test
  void nativeDeserialFlagged() {
    RuleTestHarness.assertFixture(new NativeDeserialRule(), "P6", """
        import java.io.ObjectInputStream;
        class P6 {
          Object m(ObjectInputStream in) throws Exception {
            return in.readObject(); // :: CK-NATIVE-DESERIAL
          }
        }
        """);
  }

  @Test
  void unrelatedReadObjectAllowed() {
    RuleTestHarness.assertFixture(new NativeDeserialRule(), "N5", """
        class N5 {
          Object readObject() {
            return new Object();
          }
          Object m() {
            return readObject();
          }
        }
        """);
  }
}
