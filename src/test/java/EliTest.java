import com.launchdarkly.client.LDClient;
import com.launchdarkly.client.LDConfig;
import com.launchdarkly.client.LDUser;

import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import static java.util.Collections.singletonList;

public class EliTest {

  public static void main(String... args) throws Exception {
    TrustManagerFactory trustManagerFactory = TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm());

    KeyStore truststore = KeyStore.getInstance("pkcs12");
    trustManagerFactory.init(truststore);
    TrustManager[] tms = trustManagerFactory.getTrustManagers();
    X509TrustManager trustManager = (X509TrustManager) tms[0];

    X509TrustManager dubiousManager = new X509TrustManager() {
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }

      public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("sorry");
      }

      public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("sorry");
      }
    };

    LDConfig config = new LDConfig.Builder()
        .stream(false)
        .sslSocketFactory(SSLContext.getDefault().getSocketFactory(), dubiousManager)
        .build();

    LDClient client = new LDClient("YOUR_SDK_KEY", config);

    LDUser user = new LDUser.Builder("bob@example.com").firstName("Bob").lastName("Loblaw")
        .customString("groups", singletonList("beta_testers")).build();

    boolean showFeature = client.boolVariation("YOUR_FEATURE_KEY", user, false);

    if (showFeature) {
      System.out.println("Showing your feature");
    } else {
      System.out.println("Not showing your feature");
    }

    client.flush();
    client.close();
  }
}
