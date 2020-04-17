package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@SuppressWarnings("javadoc")
public class HttpConfigurationBuilderTest {
  @Test
  public void testDefaults() {
    HttpConfiguration hc = Components.httpConfiguration().createHttpConfiguration();
    assertEquals(HttpConfigurationBuilder.DEFAULT_CONNECT_TIMEOUT, hc.getConnectTimeout());
    assertNull(hc.getProxy());
    assertNull(hc.getProxyAuthentication());
    assertEquals(HttpConfigurationBuilder.DEFAULT_SOCKET_TIMEOUT, hc.getSocketTimeout());
    assertNull(hc.getSslSocketFactory());
    assertNull(hc.getTrustManager());
    assertNull(hc.getWrapperIdentifier());
  }

  @Test
  public void testConnectTimeout() {
    HttpConfiguration hc = Components.httpConfiguration()
        .connectTimeout(Duration.ofMillis(999))
        .createHttpConfiguration();
    assertEquals(999, hc.getConnectTimeout().toMillis());
  }

  @Test
  public void testProxy() {
    HttpConfiguration hc = Components.httpConfiguration()
        .proxyHostAndPort("my-proxy", 1234)
        .createHttpConfiguration();
    assertEquals(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("my-proxy", 1234)), hc.getProxy());
    assertNull(hc.getProxyAuthentication());
  }

  @Test
  public void testProxyBasicAuth() {
    HttpConfiguration hc = Components.httpConfiguration()
        .proxyHostAndPort("my-proxy", 1234)
        .proxyAuth(Components.httpBasicAuthentication("user", "pass"))
        .createHttpConfiguration();
    assertEquals(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("my-proxy", 1234)), hc.getProxy());
    assertNotNull(hc.getProxyAuthentication());
    assertEquals("Basic dXNlcjpwYXNz", hc.getProxyAuthentication().provideAuthorization(null));
  }

  @Test
  public void testSocketTimeout() {
    HttpConfiguration hc = Components.httpConfiguration()
        .socketTimeout(Duration.ofMillis(999))
        .createHttpConfiguration();
    assertEquals(999, hc.getSocketTimeout().toMillis());
  }
  
  @Test
  public void testSslOptions() {
    SSLSocketFactory sf = new StubSSLSocketFactory();
    X509TrustManager tm = new StubX509TrustManager();
    HttpConfiguration hc = Components.httpConfiguration().sslSocketFactory(sf, tm).createHttpConfiguration();
    assertSame(sf, hc.getSslSocketFactory());
    assertSame(tm, hc.getTrustManager());
  }

  @Test
  public void testWrapperNameOnly() {
    HttpConfiguration hc = Components.httpConfiguration()
        .wrapper("Scala", null)
        .createHttpConfiguration();
    assertEquals("Scala", hc.getWrapperIdentifier());
  }

  @Test
  public void testWrapperWithVersion() {
    HttpConfiguration hc = Components.httpConfiguration()
        .wrapper("Scala", "0.1.0")
        .createHttpConfiguration();
    assertEquals("Scala/0.1.0", hc.getWrapperIdentifier());
  }

  public static class StubSSLSocketFactory extends SSLSocketFactory {
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
        throws IOException {
      return null;
    }
    
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
        throws IOException, UnknownHostException {
      return null;
    }
    
    public Socket createSocket(InetAddress host, int port) throws IOException {
      return null;
    }
    
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
      return null;
    }
    
    public String[] getSupportedCipherSuites() {
      return null;
    }
    
    public String[] getDefaultCipherSuites() {
      return null;
    }
    
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
      return null;
    }
  }
  
  public static class StubX509TrustManager implements X509TrustManager {
    public X509Certificate[] getAcceptedIssuers() {
      return null;
    }
    
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
    
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
  }
}
