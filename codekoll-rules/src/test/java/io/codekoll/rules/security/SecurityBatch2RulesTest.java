package io.codekoll.rules.security;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class SecurityBatch2RulesTest {

  @Test
  void trustAllManagerFlagged() {
    RuleTestHarness.assertFixture(new TrustAllRule(), "P1", """
        import javax.net.ssl.X509TrustManager;
        import java.security.cert.X509Certificate;
        class P1 {
          X509TrustManager m() {
            return new X509TrustManager() {
              public void checkClientTrusted(X509Certificate[] c, String a) { // :: CK-TRUST-ALL
              }
              public void checkServerTrusted(X509Certificate[] c, String a) { // :: CK-TRUST-ALL
              }
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }
            };
          }
        }
        """);
  }

  @Test
  void validatingManagerAllowed() {
    RuleTestHarness.assertFixture(new TrustAllRule(), "N1", """
        import javax.net.ssl.X509TrustManager;
        import java.security.cert.CertificateException;
        import java.security.cert.X509Certificate;
        class N1 {
          X509TrustManager m() {
            return new X509TrustManager() {
              public void checkClientTrusted(X509Certificate[] c, String a)
                  throws CertificateException {
                if (c.length == 0) {
                  throw new CertificateException("empty chain");
                }
              }
              public void checkServerTrusted(X509Certificate[] c, String a)
                  throws CertificateException {
                c[0].checkValidity();
              }
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }
            };
          }
        }
        """);
  }

  @Test
  void sqlConcatFlagged() {
    RuleTestHarness.assertFixture(new SqlConcatRule(), "P2", """
        import java.sql.Statement;
        import java.sql.ResultSet;
        import java.sql.SQLException;
        class P2 {
          ResultSet m(Statement st, String userId) throws SQLException {
            return st.executeQuery("SELECT * FROM users WHERE id = " + userId); // :: CK-SQL-CONCAT
          }
        }
        """);
  }

  @Test
  void constantSqlAndParamsAllowed() {
    RuleTestHarness.assertFixture(new SqlConcatRule(), "N2", """
        import java.sql.Connection;
        import java.sql.PreparedStatement;
        import java.sql.Statement;
        import java.sql.SQLException;
        class N2 {
          void m(Connection conn, Statement st, String userId) throws SQLException {
            st.executeQuery("SELECT * FROM users WHERE active = " + "true");
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
            ps.setString(1, userId);
          }
        }
        """);
  }

  @Test
  void execConcatFlagged() {
    RuleTestHarness.assertFixture(new ExecConcatRule(), "P3", """
        class P3 {
          Process m(String filename) throws Exception {
            return Runtime.getRuntime().exec("convert " + filename + " out.png"); // :: CK-EXEC-CONCAT
          }
        }
        """);
  }

  @Test
  void listFormProcessBuilderAllowed() {
    RuleTestHarness.assertFixture(new ExecConcatRule(), "N3", """
        class N3 {
          Process m(String filename) throws Exception {
            return new ProcessBuilder("convert", filename, "out.png").start();
          }
        }
        """);
  }
}
