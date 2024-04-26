package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.server.integrations.ApplicationInfoBuilder;
import com.launchdarkly.sdk.server.integrations.BigSegmentsConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.HooksConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.ServiceEndpointsBuilder;
import com.launchdarkly.sdk.server.integrations.WrapperInfoBuilder;
import com.launchdarkly.sdk.server.integrations.Hook;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.EventProcessor;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;

import org.junit.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestComponents.specificComponent;
import static org.easymock.EasyMock.mock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDConfigTest {
  private static final ClientContext BASIC_CONTEXT = new ClientContext("");

  @Test
  public void defaults() {
    LDConfig config = new LDConfig.Builder().build();
    assertNotNull(config.bigSegments);
    assertNull(config.bigSegments.build(clientContext("", config)).getStore());
    assertNotNull(config.dataSource);
    assertEquals(Components.streamingDataSource().getClass(), config.dataSource.getClass());
    assertNotNull(config.dataStore);
    assertEquals(Components.inMemoryDataStore().getClass(), config.dataStore.getClass());
    assertFalse(config.diagnosticOptOut);
    assertNotNull(config.events);
    assertEquals(Components.sendEvents().getClass(), config.events.getClass());
    assertFalse(config.offline);

    assertNotNull(config.http);
    HttpConfiguration httpConfig = config.http.build(BASIC_CONTEXT);
    assertEquals(HttpConfigurationBuilder.DEFAULT_CONNECT_TIMEOUT, httpConfig.getConnectTimeout());

    assertNotNull(config.logging);
    LoggingConfiguration loggingConfig = config.logging.build(BASIC_CONTEXT);
    assertEquals(LoggingConfigurationBuilder.DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER,
        loggingConfig.getLogDataSourceOutageAsErrorAfter());

    assertEquals(LDConfig.DEFAULT_START_WAIT, config.startWait);
    assertEquals(Thread.MIN_PRIORITY, config.threadPriority);
    assertNull(config.wrapperInfo);
  }

  @Test
  public void bigSegmentsConfigFactory() {
    BigSegmentsConfigurationBuilder f = Components.bigSegments(null);
    LDConfig config = new LDConfig.Builder().bigSegments(f).build();
    assertSame(f, config.bigSegments);
  }

  @Test
  public void dataSourceFactory() {
    ComponentConfigurer<DataSource> f = specificComponent(null);
    LDConfig config = new LDConfig.Builder().dataSource(f).build();
    assertSame(f, config.dataSource);
  }

  @Test
  public void dataStoreFactory() {
    ComponentConfigurer<DataStore> f = specificComponent(null);
    LDConfig config = new LDConfig.Builder().dataStore(f).build();
    assertSame(f, config.dataStore);
  }

  @Test
  public void diagnosticOptOut() {
    LDConfig config = new LDConfig.Builder().diagnosticOptOut(true).build();
    assertTrue(config.diagnosticOptOut);

    LDConfig config1 = new LDConfig.Builder().diagnosticOptOut(true).diagnosticOptOut(false).build();
    assertFalse(config1.diagnosticOptOut);
  }

  @Test
  public void eventProcessorFactory() {
    ComponentConfigurer<EventProcessor> f = specificComponent(null);
    LDConfig config = new LDConfig.Builder().events(f).build();
    assertSame(f, config.events);
  }

  @Test
  public void offline() {
    LDConfig config1 = new LDConfig.Builder().offline(true).build();
    assertTrue(config1.offline);
    assertSame(Components.externalUpdatesOnly(), config1.dataSource);
    assertSame(Components.noEvents(), config1.events);

    LDConfig config2 = new LDConfig.Builder().offline(true).dataSource(Components.streamingDataSource()).build();
    assertTrue(config2.offline);
    assertSame(Components.externalUpdatesOnly(), config2.dataSource); // offline overrides specified factory
    assertSame(Components.noEvents(), config2.events);

    LDConfig config3 = new LDConfig.Builder().offline(true).offline(false).build();
    assertFalse(config3.offline); // just testing that the setter works for both true and false
  }

  @Test
  public void hooks() {
    Hook mockHook = mock(Hook.class);
    HooksConfigurationBuilder b = Components.hooks().setHooks(Collections.singletonList(mockHook));
    LDConfig config = new LDConfig.Builder().hooks(b).build();
    assertEquals(1, config.hooks.getHooks().size());
    assertEquals(mockHook, config.hooks.getHooks().get(0));
  }

  @Test
  public void http() {
    HttpConfigurationBuilder b = Components.httpConfiguration().connectTimeout(Duration.ofSeconds(9));
    LDConfig config = new LDConfig.Builder().http(b).build();
    assertEquals(Duration.ofSeconds(9),
        config.http.build(BASIC_CONTEXT).getConnectTimeout());
  }

  @Test
  public void wrapperInfo() {
    LDConfig config = new LDConfig.Builder()
      .wrapper(Components.wrapperInfo().wrapperName("the-name").wrapperVersion("the-version")).build();
    HttpConfiguration httpConfiguration = config.http.build(
      ClientContextImpl.fromConfig("", config, null));
    AtomicBoolean headerFound = new AtomicBoolean(false);
    httpConfiguration.getDefaultHeaders().forEach(entry -> {
      if(entry.getKey().compareTo("X-LaunchDarkly-Wrapper") == 0) {
        if(entry.getValue().compareTo("the-name/the-version") == 0) {
          headerFound.set(true);
        }
      }
    });
    assertTrue(headerFound.get());
  }

  @Test
  public void logging() {
    LoggingConfigurationBuilder b = Components.logging().logDataSourceOutageAsErrorAfter(Duration.ofSeconds(9));
    LDConfig config = new LDConfig.Builder().logging(b).build();
    assertEquals(Duration.ofSeconds(9),
        config.logging.build(BASIC_CONTEXT).getLogDataSourceOutageAsErrorAfter());
  }

  @Test
  public void startWait() {
    LDConfig config = new LDConfig.Builder().startWait(Duration.ZERO).build();
    assertEquals(Duration.ZERO, config.startWait);

    LDConfig config1 = new LDConfig.Builder().startWait(Duration.ZERO).startWait(null).build();
    assertEquals(LDConfig.DEFAULT_START_WAIT, config1.startWait);
  }

  @Test
  public void threadPriority() {
    LDConfig config = new LDConfig.Builder().threadPriority(Thread.MAX_PRIORITY).build();
    assertEquals(Thread.MAX_PRIORITY, config.threadPriority);
  }

  @Test
  public void testHttpDefaults() {
    LDConfig config = new LDConfig.Builder().build();
    HttpConfiguration hc = config.http.build(BASIC_CONTEXT);
    HttpConfiguration defaults = Components.httpConfiguration().build(BASIC_CONTEXT);
    assertEquals(defaults.getConnectTimeout(), hc.getConnectTimeout());
    assertNull(hc.getProxy());
    assertNull(hc.getProxyAuthentication());
    assertEquals(defaults.getSocketTimeout(), hc.getSocketTimeout());
    assertNull(hc.getSslSocketFactory());
    assertNull(hc.getTrustManager());
    assertEquals(ImmutableMap.copyOf(defaults.getDefaultHeaders()), ImmutableMap.copyOf(hc.getDefaultHeaders()));
  }

  @Test
  public void fromConfig() {
    BigSegmentsConfigurationBuilder bigSegments = Components.bigSegments(null);
    ComponentConfigurer<DataSource> dataSource = specificComponent(null);
    ComponentConfigurer<EventProcessor> eventProcessor = specificComponent(null);
    Hook mockHook = mock(Hook.class);
    HooksConfigurationBuilder hooksBuilder = Components.hooks().setHooks(Collections.singletonList(mockHook));
    HttpConfigurationBuilder http = Components.httpConfiguration().connectTimeout(Duration.ofSeconds(9));
    WrapperInfoBuilder wrapperInfo = Components.wrapperInfo().wrapperName("the-name").wrapperVersion("the-version");
    ApplicationInfoBuilder applicationInfo = Components.applicationInfo().applicationId("test").applicationVersion("version");
    ServiceEndpointsBuilder serviceEndpoints = Components.serviceEndpoints()
      .polling("polling").streaming("stream").events("events");

    LDConfig config = new LDConfig.Builder()
      .applicationInfo(applicationInfo)
      .bigSegments(bigSegments)
      .dataSource(dataSource)
      .events(eventProcessor)
      .diagnosticOptOut(true)
      .offline(false) // To keep the data source from being removed in the build.
      .hooks(hooksBuilder)
      .http(http)
      .serviceEndpoints(serviceEndpoints)
      .wrapper(wrapperInfo).build();

    LDConfig config2 = LDConfig.Builder.fromConfig(config).build();

    assertSame(bigSegments, config2.bigSegments);
    assertSame(dataSource, config2.dataSource);
    assertSame(eventProcessor, config2.events);
    assertSame(http, config2.http);
    assertFalse(config2.offline);
    assertTrue(config2.diagnosticOptOut);
    assertEquals("test", config2.applicationInfo.getApplicationId());
    assertEquals("version", config2.applicationInfo.getApplicationVersion());
    assertEquals("the-name", config2.wrapperInfo.getWrapperName());
    assertEquals("the-version", config2.wrapperInfo.getWrapperVersion());

    assertEquals(URI.create("polling"), config2.serviceEndpoints.getPollingBaseUri());
    assertEquals(URI.create("stream"), config2.serviceEndpoints.getStreamingBaseUri());
    assertEquals(URI.create("events"), config2.serviceEndpoints.getEventsBaseUri());

    assertEquals(mockHook, config2.hooks.getHooks().get(0));
  }

  @Test
  public void fromConfigDefault() {
    LDConfig config = LDConfig.Builder.fromConfig(new LDConfig.Builder().build()).build();
    assertNotNull(config.bigSegments);
    assertNull(config.bigSegments.build(clientContext("", config)).getStore());
    assertNotNull(config.dataSource);
    assertEquals(Components.streamingDataSource().getClass(), config.dataSource.getClass());
    assertNotNull(config.dataStore);
    assertEquals(Components.inMemoryDataStore().getClass(), config.dataStore.getClass());
    assertFalse(config.diagnosticOptOut);
    assertNotNull(config.events);
    assertEquals(Components.sendEvents().getClass(), config.events.getClass());
    assertFalse(config.offline);

    assertNotNull(config.hooks.getHooks());
    assertEquals(0, config.hooks.getHooks().size());

    assertNotNull(config.http);
    HttpConfiguration httpConfig = config.http.build(BASIC_CONTEXT);
    assertEquals(HttpConfigurationBuilder.DEFAULT_CONNECT_TIMEOUT, httpConfig.getConnectTimeout());

    assertNotNull(config.logging);
    LoggingConfiguration loggingConfig = config.logging.build(BASIC_CONTEXT);
    assertEquals(LoggingConfigurationBuilder.DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER,
      loggingConfig.getLogDataSourceOutageAsErrorAfter());

    assertEquals(LDConfig.DEFAULT_START_WAIT, config.startWait);
    assertEquals(Thread.MIN_PRIORITY, config.threadPriority);
    assertNull(config.wrapperInfo);
  }
}
