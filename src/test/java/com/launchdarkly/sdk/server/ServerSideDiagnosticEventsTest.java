package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.internal.events.DiagnosticStore;
import com.launchdarkly.sdk.server.integrations.PollingDataSourceBuilder;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DiagnosticDescription;
import com.launchdarkly.sdk.server.subsystems.PersistentDataStore;

import org.junit.Test;

import java.net.URI;
import java.time.Duration;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestUtil.assertJsonEquals;
import static com.launchdarkly.testhelpers.JsonAssertions.jsonEqualsValue;
import static com.launchdarkly.testhelpers.JsonAssertions.jsonProperty;
import static com.launchdarkly.testhelpers.JsonAssertions.jsonUndefined;
import static com.launchdarkly.testhelpers.JsonTestValue.jsonFromValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class ServerSideDiagnosticEventsTest {

  private static final URI CUSTOM_URI = URI.create("http://1.1.1.1");
  
  @Test
  public void sdkDataProperties() {
    LDValue sdkData = makeSdkData(LDConfig.DEFAULT);
    assertThat(jsonFromValue(sdkData), allOf(
        jsonProperty("name", jsonEqualsValue("java-server-sdk")),
        jsonProperty("version", jsonEqualsValue(Version.SDK_VERSION)),
        jsonProperty("wrapperName", jsonUndefined()),
        jsonProperty("wrapperVersion", jsonUndefined())
        ));
  }

  @Test
  public void sdkDataWrapperProperties() {
    LDConfig config1 = new LDConfig.Builder()
        .http(Components.httpConfiguration().wrapper("Scala", "0.1.0"))
        .build();
    LDValue sdkData1 = makeSdkData(config1);
    assertThat(jsonFromValue(sdkData1), allOf(
        jsonProperty("wrapperName", jsonEqualsValue("Scala")),
        jsonProperty("wrapperVersion", jsonEqualsValue("0.1.0"))
        ));

    LDConfig config2 = new LDConfig.Builder()
        .http(Components.httpConfiguration().wrapper("Scala", null))
        .build();
    LDValue sdkData2 = makeSdkData(config2);
    assertThat(jsonFromValue(sdkData2), allOf(
        jsonProperty("wrapperName", jsonEqualsValue("Scala")),
        jsonProperty("wrapperVersion", jsonUndefined())
        ));
  }

  @Test
  public void sdkDataWrapperPropertiesUsingWrapperInfoOverridesHttpConfig() {
    LDConfig config1 = new LDConfig.Builder()
      .http(Components.httpConfiguration().wrapper("Scala", "0.1.0"))
      .wrapper(Components.wrapperInfo().wrapperName("Clojure").wrapperVersion("0.2.0"))
      .build();
    LDValue sdkData1 = makeSdkData(config1);
    assertThat(jsonFromValue(sdkData1), allOf(
      jsonProperty("wrapperName", jsonEqualsValue("Clojure")),
      jsonProperty("wrapperVersion", jsonEqualsValue("0.2.0"))
    ));

    LDConfig config2 = new LDConfig.Builder()
      .http(Components.httpConfiguration().wrapper("Scala", null))
      .wrapper(Components.wrapperInfo().wrapperName("Clojure"))
      .build();
    LDValue sdkData2 = makeSdkData(config2);
    assertThat(jsonFromValue(sdkData2), allOf(
      jsonProperty("wrapperName", jsonEqualsValue("Clojure")),
      jsonProperty("wrapperVersion", jsonUndefined())
    ));
  }

  @Test
  public void sdkDataWrapperPropertiesUsingWrapperInfo() {
    LDConfig config1 = new LDConfig.Builder()
      .wrapper(Components.wrapperInfo().wrapperName("Clojure").wrapperVersion("0.2.0"))
      .build();
    LDValue sdkData1 = makeSdkData(config1);
    assertThat(jsonFromValue(sdkData1), allOf(
      jsonProperty("wrapperName", jsonEqualsValue("Clojure")),
      jsonProperty("wrapperVersion", jsonEqualsValue("0.2.0"))
    ));

    LDConfig config2 = new LDConfig.Builder()
      .wrapper(Components.wrapperInfo().wrapperName("Clojure"))
      .build();
    LDValue sdkData2 = makeSdkData(config2);
    assertThat(jsonFromValue(sdkData2), allOf(
      jsonProperty("wrapperName", jsonEqualsValue("Clojure")),
      jsonProperty("wrapperVersion", jsonUndefined())
    ));
  }

  @Test
  public void platformDataOsNames() {
    String realOsName = System.getProperty("os.name");
    try {
      System.setProperty("os.name", "Mac OS X");
      assertThat(jsonFromValue(makePlatformData()),
          jsonProperty("osName", jsonEqualsValue("MacOS")));
      
      System.setProperty("os.name", "Windows 10");
      assertThat(jsonFromValue(makePlatformData()),
          jsonProperty("osName", jsonEqualsValue("Windows")));
      
      System.setProperty("os.name", "Linux");
      assertThat(jsonFromValue(makePlatformData()),
          jsonProperty("osName", jsonEqualsValue("Linux")));

      System.clearProperty("os.name");
      assertThat(jsonFromValue(makePlatformData()),
          jsonProperty("osName", jsonUndefined()));
    } finally {
      System.setProperty("os.name", realOsName);
    }
  }
  
  private ObjectBuilder expectedDefaultProperties() {
    return expectedDefaultPropertiesWithoutStreaming()
        .put("reconnectTimeMillis", 1_000);
  }

  private ObjectBuilder expectedDefaultPropertiesWithoutStreaming() {
    return LDValue.buildObject()
        .put("allAttributesPrivate", false)
        .put("connectTimeoutMillis", 2_000)
        .put("customBaseURI", false)
        .put("customEventsURI", false)
        .put("customStreamURI", false)
        .put("dataStoreType", "memory")
        .put("diagnosticRecordingIntervalMillis", 900_000)
        .put("eventsCapacity", 10_000)
        .put("eventsFlushIntervalMillis",5_000)
        .put("samplingInterval", 0)
        .put("socketTimeoutMillis", 10_000)
        .put("startWaitMillis", 5_000)
        .put("streamingDisabled", false)
        .put("userKeysCapacity", 1_000)
        .put("userKeysFlushIntervalMillis", 300_000)
        .put("usingProxy", false)
        .put("usingProxyAuthenticator", false)
        .put("usingRelayDaemon", false);
  }
  
  private static LDValue makeSdkData(LDConfig config) {
    return makeDiagnosticInitEvent(config).get("sdk");    
  }
  
  private static LDValue makePlatformData() {
    return makeDiagnosticInitEvent(LDConfig.DEFAULT).get("platform");
  }
  
  private static LDValue makeConfigData(LDConfig config) {
    return makeDiagnosticInitEvent(config).get("configuration");
  }
  
  private static LDValue makeDiagnosticInitEvent(LDConfig config) {
    ClientContext context = clientContext("SDK_KEY", config); // the SDK key doesn't matter for these tests
    DiagnosticStore diagnosticStore = new DiagnosticStore(
        ServerSideDiagnosticEvents.getSdkDiagnosticParams(context, config));
    return diagnosticStore.getInitEvent().getJsonValue();    
  }
  
  @Test
  public void testDefaultDiagnosticConfiguration() {
    LDConfig ldConfig = new LDConfig.Builder().build();
    LDValue diagnosticJson = makeConfigData(ldConfig);
    LDValue expected = expectedDefaultProperties().build();

    assertEquals(expected, diagnosticJson);
  }

  @Test
  public void testCustomDiagnosticConfigurationGeneralProperties() {
    LDConfig ldConfig = new LDConfig.Builder()
            .startWait(Duration.ofSeconds(10))
            .build();

    LDValue diagnosticJson = makeConfigData(ldConfig);
    LDValue expected = expectedDefaultProperties()
        .put("startWaitMillis", 10_000)
        .build();

    assertJsonEquals(expected, diagnosticJson);
  }

  @Test
  public void testCustomDiagnosticConfigurationForServiceEndpoints() {
    LDConfig ldConfig1 = new LDConfig.Builder()
        .serviceEndpoints(
            Components.serviceEndpoints()
              .streaming(CUSTOM_URI)
              .events(CUSTOM_URI)
              // this shouldn't show up in diagnostics because we don't use the polling component
              .polling(CUSTOM_URI)
        )
        .build();
    LDValue expected1 = expectedDefaultProperties()
        .put("customStreamURI", true)
        .put("customEventsURI", true)
        .build();
    assertJsonEquals(expected1, makeConfigData(ldConfig1));

    LDConfig ldConfig2 = new LDConfig.Builder()
        .serviceEndpoints(
            Components.serviceEndpoints()
              .events(CUSTOM_URI)
              .polling(CUSTOM_URI)
        )
        .dataSource(
            Components.pollingDataSource()
        )
        .events(Components.sendEvents())
        .build();
    LDValue expected2 = expectedDefaultPropertiesWithoutStreaming()
        .put("customBaseURI", true)
        .put("customEventsURI", true)
        .put("customStreamURI", false)
        .put("pollingIntervalMillis", PollingDataSourceBuilder.DEFAULT_POLL_INTERVAL.toMillis())
        .put("streamingDisabled", true)
        .build();
    assertJsonEquals(expected2, makeConfigData(ldConfig2));
  }
  
  @Test
  public void testCustomDiagnosticConfigurationForStreaming() {
    LDConfig ldConfig1 = new LDConfig.Builder()
            .dataSource(
                Components.streamingDataSource()
                  .initialReconnectDelay(Duration.ofSeconds(2))
                )
            .build();
    LDValue expected1 = expectedDefaultPropertiesWithoutStreaming()
        .put("reconnectTimeMillis", 2_000)
        .build();
    assertJsonEquals(expected1, makeConfigData(ldConfig1));
    
    LDConfig ldConfig2 = new LDConfig.Builder()
        .dataSource(Components.streamingDataSource()) // no custom base URIs
        .build();
    LDValue expected2 = expectedDefaultProperties().build();
    assertEquals(expected2, makeConfigData(ldConfig2));
  }

  @Test
  public void testCustomDiagnosticConfigurationForPolling() {
    LDConfig ldConfig1 = new LDConfig.Builder()
            .dataSource(
                Components.pollingDataSource()
                  .pollInterval(Duration.ofSeconds(60))
                )
            .build();
    LDValue expected1 = expectedDefaultPropertiesWithoutStreaming()
        .put("pollingIntervalMillis", 60_000)
        .put("streamingDisabled", true)
        .build();
    assertJsonEquals(expected1,  makeConfigData(ldConfig1));

    LDConfig ldConfig2 = new LDConfig.Builder()
        .dataSource(Components.pollingDataSource()) // no custom base URI
        .build();
    LDValue expected2 = expectedDefaultPropertiesWithoutStreaming()
        .put("pollingIntervalMillis", PollingDataSourceBuilder.DEFAULT_POLL_INTERVAL.toMillis())
        .put("streamingDisabled", true)
        .build();
    assertJsonEquals(expected2, makeConfigData(ldConfig2));
  }

  @Test
  public void testCustomDiagnosticConfigurationForCustomDataStore() {
    LDConfig ldConfig1 = new LDConfig.Builder()
        .dataStore(new DataStoreFactoryWithDiagnosticDescription(LDValue.of("my-test-store")))
        .build();
    LDValue expected1 = expectedDefaultProperties().put("dataStoreType", "my-test-store").build();
    assertJsonEquals(expected1, makeConfigData(ldConfig1));

    LDConfig ldConfig2 = new LDConfig.Builder()
        .dataStore(new DataStoreFactoryWithoutDiagnosticDescription())
        .build();
    LDValue expected2 = expectedDefaultProperties().put("dataStoreType", "custom").build();
    assertJsonEquals(expected2, makeConfigData(ldConfig2));

    LDConfig ldConfig3 = new LDConfig.Builder()
        .dataStore(new DataStoreFactoryWithDiagnosticDescription(null))
        .build();
    LDValue expected3 = expectedDefaultProperties().put("dataStoreType", "custom").build();
    assertJsonEquals(expected3, makeConfigData(ldConfig3));

    LDConfig ldConfig4 = new LDConfig.Builder()
        .dataStore(new DataStoreFactoryWithDiagnosticDescription(LDValue.of(4)))
        .build();
    LDValue expected4 = expectedDefaultProperties().put("dataStoreType", "custom").build();
    assertJsonEquals(expected4, makeConfigData(ldConfig4));
  }

  @Test
  public void testCustomDiagnosticConfigurationForPersistentDataStore() {
    LDConfig ldConfig1 = new LDConfig.Builder()
        .dataStore(Components.persistentDataStore(new PersistentDataStoreFactoryWithComponentName()))
        .build();

    LDValue diagnosticJson1 = makeConfigData(ldConfig1);
    LDValue expected1 = expectedDefaultProperties().put("dataStoreType", "my-test-store").build();

    assertJsonEquals(expected1, diagnosticJson1);

    LDConfig ldConfig2 = new LDConfig.Builder()
        .dataStore(Components.persistentDataStore(new PersistentDataStoreFactoryWithoutComponentName()))
        .build();

    LDValue diagnosticJson2 = makeConfigData(ldConfig2);
    LDValue expected2 = expectedDefaultProperties().put("dataStoreType", "custom").build();

    assertJsonEquals(expected2, diagnosticJson2);
  }

  @Test
  public void testCustomDiagnosticConfigurationForEvents() {
    LDConfig ldConfig1 = new LDConfig.Builder()
          .events(
              Components.sendEvents()
                .allAttributesPrivate(true)
                .capacity(20_000)
                .diagnosticRecordingInterval(Duration.ofSeconds(1_800))
                .flushInterval(Duration.ofSeconds(10))
                .userKeysCapacity(2_000)
                .userKeysFlushInterval(Duration.ofSeconds(600))
              )
          .build();

    LDValue diagnosticJson1 = makeConfigData(ldConfig1);
    LDValue expected1 = expectedDefaultProperties()
        .put("allAttributesPrivate", true)
        .put("diagnosticRecordingIntervalMillis", 1_800_000)
        .put("eventsCapacity", 20_000)
        .put("eventsFlushIntervalMillis", 10_000)
        .put("userKeysCapacity", 2_000)
        .put("userKeysFlushIntervalMillis", 600_000)
        .build();

    assertJsonEquals(expected1, diagnosticJson1);
    
    LDConfig ldConfig2 = new LDConfig.Builder()
        .events(Components.sendEvents()) // no custom base URI
        .build();
    
    LDValue diagnosticJson2 = makeConfigData(ldConfig2);
    LDValue expected2 = expectedDefaultProperties().build();
    
    assertJsonEquals(expected2, diagnosticJson2);
  }

  @Test
  public void testCustomDiagnosticConfigurationForDaemonMode() {
    LDConfig ldConfig = new LDConfig.Builder()
            .dataSource(Components.externalUpdatesOnly())
            .build();

    LDValue diagnosticJson = makeConfigData(ldConfig);
    LDValue expected = expectedDefaultPropertiesWithoutStreaming()
        .put("usingRelayDaemon", true)
        .build();

    assertJsonEquals(expected, diagnosticJson);
  }

  @Test
  public void testCustomDiagnosticConfigurationHttpProperties() {
    LDConfig ldConfig = new LDConfig.Builder()
        .http(
            Components.httpConfiguration()
              .connectTimeout(Duration.ofSeconds(5))
              .socketTimeout(Duration.ofSeconds(20))
              .proxyHostAndPort("localhost", 1234)
              .proxyAuth(Components.httpBasicAuthentication("username", "password"))
        )
        .build();

    LDValue diagnosticJson = makeConfigData(ldConfig);
    LDValue expected = expectedDefaultProperties()
        .put("connectTimeoutMillis", 5_000)
        .put("socketTimeoutMillis",  20_000)
        .put("usingProxy", true)
        .put("usingProxyAuthenticator", true)
        .build();

    assertJsonEquals(expected, diagnosticJson);
  }
  
  @Test
  public void customComponentCannotInjectUnsupportedConfigProperty() {
    String unsupportedPropertyName = "fake";
    LDValue description = LDValue.buildObject().put(unsupportedPropertyName, true).build();
    LDConfig config = new LDConfig.Builder()
        .dataSource(new DataSourceFactoryWithDiagnosticDescription(description))
        .build();
    
    LDValue diagnosticJson = makeConfigData(config);
    
    assertThat(jsonFromValue(diagnosticJson), jsonProperty(unsupportedPropertyName, jsonUndefined()));
  }

  @Test
  public void customComponentCannotInjectSupportedConfigPropertyWithWrongType() {
    LDValue description = LDValue.buildObject().put("streamingDisabled", 3).build();
    LDConfig config = new LDConfig.Builder()
        .dataSource(new DataSourceFactoryWithDiagnosticDescription(description))
        .build();
    
    LDValue diagnosticJson = makeConfigData(config);
    
    assertThat(jsonFromValue(diagnosticJson), jsonProperty("streamingDisabled", jsonUndefined()));
  }

  @Test
  public void customComponentDescriptionOfUnsupportedTypeIsIgnored() {
    LDConfig config1 = new LDConfig.Builder()
        .dataSource(new DataSourceFactoryWithDiagnosticDescription(LDValue.of(3)))
        .build();
    LDConfig config2 = new LDConfig.Builder()
        .dataSource(new DataSourceFactoryWithoutDiagnosticDescription())
        .build();
    
    LDValue diagnosticJson1 = makeConfigData(config1);
    LDValue diagnosticJson2 = makeConfigData(config2);
    
    assertJsonEquals(diagnosticJson1, diagnosticJson2);
  }

  private static class DataSourceFactoryWithDiagnosticDescription implements ComponentConfigurer<DataSource>, DiagnosticDescription {
    private final LDValue value;
    
    DataSourceFactoryWithDiagnosticDescription(LDValue value) {
      this.value = value;
    }
    
    @Override
    public LDValue describeConfiguration(ClientContext clientContext) {
      return value;
    }

    @Override
    public DataSource build(ClientContext context) {
      return null;
    }
  }

  private static class DataSourceFactoryWithoutDiagnosticDescription implements ComponentConfigurer<DataSource> {
    @Override
    public DataSource build(ClientContext context) {
      return null;
    }
  }

  private static class DataStoreFactoryWithDiagnosticDescription implements ComponentConfigurer<DataStore>, DiagnosticDescription {
    private final LDValue value;
    
    DataStoreFactoryWithDiagnosticDescription(LDValue value) {
      this.value = value;
    }
    
    @Override
    public LDValue describeConfiguration(ClientContext clientContext) {
      return value;
    }

    @Override
    public DataStore build(ClientContext context) {
      return null;
    }
  }

  private static class DataStoreFactoryWithoutDiagnosticDescription implements ComponentConfigurer<DataStore> {
    @Override
    public DataStore build(ClientContext context) {
      return null;
    }
  }
  
  private static class PersistentDataStoreFactoryWithComponentName implements ComponentConfigurer<PersistentDataStore>, DiagnosticDescription {
    @Override
    public LDValue describeConfiguration(ClientContext clientContext) {
      return LDValue.of("my-test-store");
    }

    @Override
    public PersistentDataStore build(ClientContext context) {
      return null;
    }
  }
  
  private static class PersistentDataStoreFactoryWithoutComponentName implements ComponentConfigurer<PersistentDataStore> {
    @Override
    public PersistentDataStore build(ClientContext context) {
      return null;
    }
  }
}
