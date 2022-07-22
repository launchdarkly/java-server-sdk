package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;

import org.junit.Test;

import java.time.Duration;

import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class ClientContextImplTest {
  private static final String SDK_KEY = "sdk-key";
  
  @Test
  public void getBasicDefaultProperties() {
    LDConfig config = new LDConfig.Builder().build();

    ClientContext c = ClientContextImpl.fromConfig(SDK_KEY, config, null);
    
    assertEquals(SDK_KEY, c.getSdkKey());
    assertFalse(c.isOffline());
    assertEquals(Thread.MIN_PRIORITY, c.getThreadPriority());
    
    HttpConfiguration httpConfig = c.getHttp();
    assertEquals(HttpConfigurationBuilder.DEFAULT_CONNECT_TIMEOUT, httpConfig.getConnectTimeout());
    
    LoggingConfiguration loggingConfig = c.getLogging();
    assertEquals(LoggingConfigurationBuilder.DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER,
        loggingConfig.getLogDataSourceOutageAsErrorAfter());
  }
  
  @Test
  public void getBasicPropertiesWithCustomConfig() {
    LDConfig config = new LDConfig.Builder()
        .http(Components.httpConfiguration().connectTimeout(Duration.ofSeconds(10)))
        .logging(Components.logging().logDataSourceOutageAsErrorAfter(Duration.ofMinutes(20)))
        .offline(true)
        .threadPriority(Thread.MAX_PRIORITY)
        .build();
 
    ClientContext c = ClientContextImpl.fromConfig(SDK_KEY, config, sharedExecutor);
    
    assertEquals(SDK_KEY, c.getSdkKey());
    assertTrue(c.isOffline());
    assertEquals(Thread.MAX_PRIORITY, c.getThreadPriority());

    HttpConfiguration httpConfig = c.getHttp();
    assertEquals(Duration.ofSeconds(10), httpConfig.getConnectTimeout());
    
    LoggingConfiguration loggingConfig = c.getLogging();
    assertEquals(Duration.ofMinutes(20), loggingConfig.getLogDataSourceOutageAsErrorAfter());
  }
  
  @Test
  public void getPackagePrivateSharedExecutor() {
    LDConfig config = new LDConfig.Builder().build();

    ClientContext c = ClientContextImpl.fromConfig(SDK_KEY, config, sharedExecutor);

    assertSame(sharedExecutor, ClientContextImpl.get(c).sharedExecutor);
  }
  
  @Test
  public void getPackagePrivateDiagnosticAccumulator() {
    LDConfig config = new LDConfig.Builder().build();

    ClientContext c = ClientContextImpl.fromConfig(SDK_KEY, config, sharedExecutor);

    assertNotNull(ClientContextImpl.get(c).diagnosticStore);
  }

  @Test
  public void diagnosticStoreIsNullIfOptedOut() {
    LDConfig config = new LDConfig.Builder()
        .diagnosticOptOut(true)
        .build();

    ClientContext c = ClientContextImpl.fromConfig(SDK_KEY, config, sharedExecutor);

    assertNull(ClientContextImpl.get(c).diagnosticStore);
  }
  
  @Test
  public void packagePrivatePropertiesHaveDefaultsIfContextIsNotOurImplementation() {
    // This covers a scenario where a user has created their own ClientContext and it has been
    // passed to one of our SDK components.
    ClientContext c = new ClientContext(SDK_KEY);
    
    ClientContextImpl impl = ClientContextImpl.get(c);
    
    assertNotNull(impl.sharedExecutor);
    assertNull(impl.diagnosticStore);
    
    ClientContextImpl impl2 = ClientContextImpl.get(c);
    
    assertNotNull(impl2.sharedExecutor);
    assertSame(impl.sharedExecutor, impl2.sharedExecutor);
  }
}
