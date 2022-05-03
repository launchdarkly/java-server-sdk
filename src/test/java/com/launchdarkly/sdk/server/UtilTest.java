package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.ApplicationInfo;
import com.launchdarkly.sdk.server.interfaces.HttpAuthentication;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import org.junit.Test;

import java.time.Duration;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.Util.configureHttpClientBuilder;
import static com.launchdarkly.sdk.server.Util.shutdownHttpClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import okhttp3.Authenticator;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

@SuppressWarnings("javadoc")
public class UtilTest {
  @Test
  public void testConnectTimeout() {
    LDConfig config = new LDConfig.Builder().http(Components.httpConfiguration().connectTimeout(Duration.ofSeconds(3))).build();
    HttpConfiguration httpConfig = clientContext("", config).getHttp();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(httpConfig, httpBuilder);
    OkHttpClient httpClient = httpBuilder.build();
    try {
      assertEquals(3000, httpClient.connectTimeoutMillis());
    } finally {
      shutdownHttpClient(httpClient);
    }
  }
  
  @Test
  public void testSocketTimeout() {
    LDConfig config = new LDConfig.Builder().http(Components.httpConfiguration().socketTimeout(Duration.ofSeconds(3))).build();
    HttpConfiguration httpConfig = clientContext("", config).getHttp();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(httpConfig, httpBuilder);
    OkHttpClient httpClient = httpBuilder.build();
    try {
      assertEquals(3000, httpClient.readTimeoutMillis());
    } finally {
      shutdownHttpClient(httpClient);
    }
  }
  
  @Test
  public void useOurBasicAuthenticatorAsOkhttpProxyAuthenticator() throws Exception {
    HttpAuthentication ourAuth = Components.httpBasicAuthentication("user", "pass");
    Authenticator okhttpAuth = Util.okhttpAuthenticatorFromHttpAuthStrategy(ourAuth,
        "Proxy-Authentication", "Proxy-Authorization");
    
    Request originalRequest = new Request.Builder().url("http://proxy").build();
    Response resp1 = new Response.Builder()
        .request(originalRequest)
        .message("")
        .protocol(Protocol.HTTP_1_1)
        .header("Proxy-Authentication", "Basic realm=x")
        .code(407)
        .build();
    
    Request newRequest = okhttpAuth.authenticate(null, resp1);
    
    assertEquals("Basic dXNlcjpwYXNz", newRequest.header("Proxy-Authorization"));

    // simulate the proxy rejecting us again
    Response resp2 = new Response.Builder()
        .request(newRequest)
        .message("")
        .protocol(Protocol.HTTP_1_1)
        .header("Proxy-Authentication", "Basic realm=x")
        .code(407)
        .build();
    
    assertNull(okhttpAuth.authenticate(null, resp2)); // null tells OkHttp to give up
  }

  @Test
  public void describeDuration() {
    assertEquals("15 milliseconds", Util.describeDuration(Duration.ofMillis(15)));
    assertEquals("1500 milliseconds", Util.describeDuration(Duration.ofMillis(1500)));
    assertEquals("1 second", Util.describeDuration(Duration.ofMillis(1000)));
    assertEquals("2 seconds", Util.describeDuration(Duration.ofMillis(2000)));
    assertEquals("70 seconds", Util.describeDuration(Duration.ofMillis(70000)));
    assertEquals("1 minute", Util.describeDuration(Duration.ofMillis(60000)));
    assertEquals("2 minutes", Util.describeDuration(Duration.ofMillis(120000)));
  }

  @Test
  public void applicationTagHeader() {
    assertEquals("", Util.applicationTagHeader(new ApplicationInfo(null, null)));
    assertEquals("application-id/foo", Util.applicationTagHeader(new ApplicationInfo("foo", null)));
    assertEquals("application-version/1.0.0", Util.applicationTagHeader(new ApplicationInfo(null, "1.0.0")));
    assertEquals("application-id/foo application-version/1.0.0", Util.applicationTagHeader(new ApplicationInfo("foo", "1.0.0")));
    // Values with invalid characters get discarded
    assertEquals("", Util.applicationTagHeader(new ApplicationInfo("invalid name", "lol!")));
    // Values over 64 chars get discarded
    assertEquals("", Util.applicationTagHeader(new ApplicationInfo("look-at-this-incredibly-long-application-id-like-wow-it-sure-is-verbose", null)));
    // Empty values get discarded
    assertEquals("", Util.applicationTagHeader(new ApplicationInfo("", "")));
  }
}
