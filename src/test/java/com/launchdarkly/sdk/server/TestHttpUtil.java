package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;
import com.launchdarkly.testhelpers.httptest.Handler;
import com.launchdarkly.testhelpers.httptest.HttpServer;
import com.launchdarkly.testhelpers.httptest.RequestInfo;
import com.launchdarkly.testhelpers.httptest.ServerTLSConfiguration;

import java.io.IOException;
import java.net.URI;

import static com.launchdarkly.sdk.server.TestUtil.makeSocketFactorySingleHost;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

class TestHttpUtil {
  // Used for testWithSpecialHttpConfigurations
  static interface HttpConfigurationTestAction {
    void accept(URI targetUri, ComponentConfigurer<HttpConfiguration> httpConfig) throws IOException;
  }
  
  /**
   * A test suite for all SDK components that support our standard HTTP configuration options.
   * <p>
   * Although all of our supported HTTP behaviors are implemented in shared code, there is no
   * guarantee that all of our components are using that code, or using it correctly. So we
   * should run this test suite on each component that can be affected by HttpConfigurationBuilder
   * properties. It works as follows:
   * <ul>
   * <li> For each HTTP configuration variant that is expected to work (trusted certificate;
   * proxy server; etc.), set up a server that will produce whatever expected response was
   * specified in {@code handler}. Then run {@code testActionShouldSucceed}, which should create
   * its component with the given configuration and base URI and verify that the component
   * behaves correctly.
   * <li> Do the same for each HTTP configuration variant that is expected to fail, but run
   * {@code testActionShouldFail} instead.
   * </ul>
   * 
   * @param handler the response that the server should provide for all requests
   * @param testActionShouldSucceed an action that asserts that the component works
   * @param testActionShouldFail an action that asserts that the component does not work
   * @throws IOException
   */
  static void testWithSpecialHttpConfigurations(
      Handler handler,
      HttpConfigurationTestAction testActionShouldSucceed,
      HttpConfigurationTestAction testActionShouldFail
      ) throws IOException {

    testHttpClientDoesNotAllowSelfSignedCertByDefault(handler, testActionShouldFail);
    testHttpClientCanBeConfiguredToAllowSelfSignedCert(handler, testActionShouldSucceed);
    testHttpClientCanUseCustomSocketFactory(handler, testActionShouldSucceed);
    testHttpClientCanUseProxy(handler, testActionShouldSucceed);
    testHttpClientCanUseProxyWithBasicAuth(handler, testActionShouldSucceed);
  }
  
  static void testHttpClientDoesNotAllowSelfSignedCertByDefault(Handler handler,
      HttpConfigurationTestAction testActionShouldFail) {
    try {
      ServerTLSConfiguration tlsConfig = ServerTLSConfiguration.makeSelfSignedCertificate();
      try (HttpServer secureServer = HttpServer.startSecure(tlsConfig, handler)) {
        testActionShouldFail.accept(secureServer.getUri(), Components.httpConfiguration());
        assertThat(secureServer.getRecorder().count(), equalTo(0));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static void testHttpClientCanBeConfiguredToAllowSelfSignedCert(Handler handler,
      HttpConfigurationTestAction testActionShouldSucceed) {
    try {
      ServerTLSConfiguration tlsConfig = ServerTLSConfiguration.makeSelfSignedCertificate();
      ComponentConfigurer<HttpConfiguration> httpConfig = Components.httpConfiguration()
          .sslSocketFactory(tlsConfig.getSocketFactory(), tlsConfig.getTrustManager());
      try (HttpServer secureServer = HttpServer.startSecure(tlsConfig, handler)) {
        testActionShouldSucceed.accept(secureServer.getUri(), httpConfig);
        assertThat(secureServer.getRecorder().count(), equalTo(1));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static void testHttpClientCanUseCustomSocketFactory(Handler handler,
      HttpConfigurationTestAction testActionShouldSucceed) {
    try {
      try (HttpServer server = HttpServer.start(handler)) {
        ComponentConfigurer<HttpConfiguration> httpConfig = Components.httpConfiguration()
            .socketFactory(makeSocketFactorySingleHost(server.getUri().getHost(), server.getPort()));

        URI uriWithWrongPort = URI.create("http://localhost:1");
        testActionShouldSucceed.accept(uriWithWrongPort, httpConfig);
        assertThat(server.getRecorder().count(), equalTo(1));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  static void testHttpClientCanUseProxy(Handler handler,
      HttpConfigurationTestAction testActionShouldSucceed) {
    try {
      try (HttpServer server = HttpServer.start(handler)) {
        ComponentConfigurer<HttpConfiguration> httpConfig = Components.httpConfiguration()
            .proxyHostAndPort(server.getUri().getHost(), server.getPort());
        
        URI fakeBaseUri = URI.create("http://not-a-real-host");
        testActionShouldSucceed.accept(fakeBaseUri, httpConfig);
        assertThat(server.getRecorder().count(), equalTo(1));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static void testHttpClientCanUseProxyWithBasicAuth(Handler handler,
      HttpConfigurationTestAction testActionShouldSucceed) {
    Handler proxyHandler = ctx -> {
      if (ctx.getRequest().getHeader("Proxy-Authorization") == null) {
        ctx.setStatus(407);
        ctx.setHeader("Proxy-Authenticate", "Basic realm=x");
      } else {
        handler.apply(ctx);
      }
    };
    try {
      try (HttpServer server = HttpServer.start(proxyHandler)) {
        ComponentConfigurer<HttpConfiguration> httpConfig = Components.httpConfiguration()
            .proxyHostAndPort(server.getUri().getHost(), server.getPort())
            .proxyAuth(Components.httpBasicAuthentication("user", "pass"));
        
        URI fakeBaseUri = URI.create("http://not-a-real-host");
        testActionShouldSucceed.accept(fakeBaseUri, httpConfig);
        
        assertThat(server.getRecorder().count(), equalTo(2));
        RequestInfo req1 = server.getRecorder().requireRequest();
        assertThat(req1.getHeader("Proxy-Authorization"), nullValue());
        RequestInfo req2 = server.getRecorder().requireRequest();
        assertThat(req2.getHeader("Proxy-Authorization"), equalTo("Basic dXNlcjpwYXNz"));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
