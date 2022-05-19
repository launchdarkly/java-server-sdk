package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;
import com.launchdarkly.sdk.server.interfaces.BasicConfiguration;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;
import com.launchdarkly.sdk.server.interfaces.LoggingConfiguration;

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

    ClientContext c = new ClientContextImpl(SDK_KEY, config, null, null);
    
    BasicConfiguration basicConfig = c.getBasic();
    assertEquals(SDK_KEY, basicConfig.getSdkKey());
    assertFalse(basicConfig.isOffline());
    assertEquals(Thread.MIN_PRIORITY, basicConfig.getThreadPriority());
    
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
 
    ClientContext c = new ClientContextImpl(SDK_KEY, config, sharedExecutor, null);
    
    BasicConfiguration basicConfig = c.getBasic();
    assertEquals(SDK_KEY, basicConfig.getSdkKey());
    assertTrue(basicConfig.isOffline());
    assertEquals(Thread.MAX_PRIORITY, basicConfig.getThreadPriority());

    HttpConfiguration httpConfig = c.getHttp();
    assertEquals(Duration.ofSeconds(10), httpConfig.getConnectTimeout());
    
    LoggingConfiguration loggingConfig = c.getLogging();
    assertEquals(Duration.ofMinutes(20), loggingConfig.getLogDataSourceOutageAsErrorAfter());
  }
  
  @Test
  public void getPackagePrivateSharedExecutor() {
    LDConfig config = new LDConfig.Builder().build();

    ClientContext c = new ClientContextImpl(SDK_KEY, config, sharedExecutor, null);

    assertSame(sharedExecutor, ClientContextImpl.get(c).sharedExecutor);
  }
  
  @Test
  public void getPackagePrivateDiagnosticAccumulator() {
    LDConfig config = new LDConfig.Builder().build();

    DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
    
    ClientContext c = new ClientContextImpl(SDK_KEY, config, sharedExecutor, diagnosticAccumulator);

    assertSame(diagnosticAccumulator, ClientContextImpl.get(c).diagnosticAccumulator);
  }

  @Test
  public void diagnosticAccumulatorIsNullIfOptedOut() {
    LDConfig config = new LDConfig.Builder()
        .diagnosticOptOut(true)
        .build();

    DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
    
    ClientContext c = new ClientContextImpl(SDK_KEY, config, sharedExecutor, diagnosticAccumulator);

    assertNull(ClientContextImpl.get(c).diagnosticAccumulator);
    assertNull(ClientContextImpl.get(c).diagnosticInitEvent);
  }
  
  @Test
  public void getPackagePrivateDiagnosticInitEvent() {
    LDConfig config = new LDConfig.Builder().build();

    DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
    
    ClientContext c = new ClientContextImpl(SDK_KEY, config, sharedExecutor, diagnosticAccumulator);

    assertNotNull(ClientContextImpl.get(c).diagnosticInitEvent);
  }
  
  @Test
  public void packagePrivatePropertiesHaveDefaultsIfContextIsNotOurImplementation() {
    // This covers a scenario where a user has created their own ClientContext and it has been
    // passed to one of our SDK components.
    ClientContext c = new SomeOtherContextImpl();
    
    ClientContextImpl impl = ClientContextImpl.get(c);
    
    assertNotNull(impl.sharedExecutor);
    assertNull(impl.diagnosticAccumulator);
    assertNull(impl.diagnosticInitEvent);
    
    ClientContextImpl impl2 = ClientContextImpl.get(c);
    
    assertNotNull(impl2.sharedExecutor);
    assertSame(impl.sharedExecutor, impl2.sharedExecutor);
  }
  
  private static final class SomeOtherContextImpl implements ClientContext {
    public BasicConfiguration getBasic() {
      return new BasicConfiguration(SDK_KEY, false, Thread.MIN_PRIORITY, null, null);
    }

    public HttpConfiguration getHttp() {
      return null;
    }
    
    public LoggingConfiguration getLogging() {
      return null;
    }
  }
}
