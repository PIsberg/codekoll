package examples.security;

import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

/**
 * Example for rule {@code CK-TRUST-ALL}.
 *
 * <p><b>What is wrong:</b> {@code buggy}'s {@code checkServerTrusted} is empty — it accepts
 * any certificate.
 *
 * <p><b>What happens at runtime:</b> TLS still encrypts, but validates nothing: a
 * man-in-the-middle presenting a self-signed or forged certificate is accepted, and reads
 * and rewrites every byte. These "just to make the demo work" trust managers reach
 * production with alarming regularity.
 *
 * <p><b>How to fix it:</b> validate the chain — the {@code fixed} manager delegates to the
 * platform default. Never bypass validation.
 */
public class TrustAllExample {

  static class buggy implements X509TrustManager {
    @Override // :: CK-TRUST-ALL
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
    }

    @Override // :: CK-TRUST-ALL
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }

  static class fixed implements X509TrustManager {
    private final X509TrustManager delegate;

    fixed(X509TrustManager delegate) {
      this.delegate = delegate;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws java.security.cert.CertificateException {
      delegate.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
        throws java.security.cert.CertificateException {
      delegate.checkServerTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return delegate.getAcceptedIssuers();
    }
  }
}
