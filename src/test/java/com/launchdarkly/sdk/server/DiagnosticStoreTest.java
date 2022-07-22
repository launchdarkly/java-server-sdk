package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DiagnosticStore.SdkDiagnosticParams;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.launchdarkly.testhelpers.JsonAssertions.jsonEqualsValue;
import static com.launchdarkly.testhelpers.JsonAssertions.jsonProperty;
import static com.launchdarkly.testhelpers.JsonAssertions.jsonUndefined;
import static com.launchdarkly.testhelpers.JsonTestValue.jsonFromValue;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

@SuppressWarnings("javadoc")
public class DiagnosticStoreTest {
  private static final String SDK_KEY = "key-abcdefg";
  private static final String SDK_NAME = "fake-sdk";
  private static final String SDK_VERSION = "1.2.3";
  private static final String PLATFORM_NAME = "fake-platform";
  
  @Test
  public void initEventBasicProperties() {
    long now = System.currentTimeMillis();
    DiagnosticStore store = makeSimpleStore();
    DiagnosticEvent.Init ie = store.getInitEvent();
    assertThat(ie.kind, equalTo("diagnostic-init"));
    assertThat(ie.creationDate, greaterThanOrEqualTo(now));
    assertThat(ie.id, notNullValue());
    assertThat(ie.id.diagnosticId, notNullValue());
    assertThat(ie.id.sdkKeySuffix, equalTo("bcdefg"));
  }
  
  @Test
  public void initEventSdkData() {
    DiagnosticStore store = makeSimpleStore();
    DiagnosticEvent.Init ie = store.getInitEvent();
    assertThat(jsonFromValue(ie.sdk), allOf(
        jsonProperty("name", SDK_NAME),
        jsonProperty("version", SDK_VERSION),
        jsonProperty("wrapperName", jsonUndefined()),
        jsonProperty("wrapperVersion", jsonUndefined())
        ));
  }

  @Test
  public void initEventSdkDataWithWrapperName() {
    DiagnosticStore store = new DiagnosticStore(new SdkDiagnosticParams(
        SDK_KEY, SDK_NAME, SDK_VERSION, PLATFORM_NAME, null,
        singletonMap("X-LaunchDarkly-Wrapper", "Scala"),
        null
        ));
    DiagnosticEvent.Init ie = store.getInitEvent();
    assertThat(jsonFromValue(ie.sdk), allOf(
        jsonProperty("name", SDK_NAME),
        jsonProperty("version", SDK_VERSION),
        jsonProperty("wrapperName", "Scala"),
        jsonProperty("wrapperVersion", jsonUndefined())
        ));
  }

  @Test
  public void initEventSdkDataWithWrapperNameAndVersion() {
    DiagnosticStore store = new DiagnosticStore(new SdkDiagnosticParams(
        SDK_KEY, SDK_NAME, SDK_VERSION, PLATFORM_NAME, null,
        singletonMap("X-LaunchDarkly-Wrapper", "Scala/0.1"),
        null
        ));
    DiagnosticEvent.Init ie = store.getInitEvent();
    assertThat(jsonFromValue(ie.sdk), allOf(
        jsonProperty("name", SDK_NAME),
        jsonProperty("version", SDK_VERSION),
        jsonProperty("wrapperName", "Scala"),
        jsonProperty("wrapperVersion", "0.1")
        ));
  }
  
  @Test
  public void platformDataFromSdk() {
    DiagnosticStore store = new DiagnosticStore(new SdkDiagnosticParams(
        SDK_KEY, SDK_NAME, SDK_VERSION, PLATFORM_NAME,
        LDValue.buildObject().put("prop1", 2).put("prop2", 3).build(),
        null, null
        ));
    DiagnosticEvent.Init ie = store.getInitEvent();
    assertThat(jsonFromValue(ie.platform), allOf(
        jsonProperty("name", PLATFORM_NAME),
        jsonProperty("prop1", 2),
        jsonProperty("prop2", 3)
        ));
  }
  
  @Test
  public void configurationData() {
    List<LDValue> configValues = Arrays.asList(
        LDValue.buildObject()
          .put(DiagnosticConfigProperty.EVENTS_CAPACITY.name, 1000)
          .put(DiagnosticConfigProperty.USER_KEYS_CAPACITY.name, 2000)
          .put(DiagnosticConfigProperty.ALL_ATTRIBUTES_PRIVATE.name, "yes") // ignored because of wrong type
          .build(),
        LDValue.of("abcdef"), // ignored because it's not an object
        null, // no-op
        LDValue.buildObject().put(DiagnosticConfigProperty.DATA_STORE_TYPE.name, "custom").build()
        );
    DiagnosticStore store = new DiagnosticStore(new SdkDiagnosticParams(
        SDK_KEY, SDK_NAME, SDK_VERSION, PLATFORM_NAME, null, null,
        configValues
        ));
    DiagnosticEvent.Init ie = store.getInitEvent();
    assertThat(jsonFromValue(ie.configuration), jsonEqualsValue(
        LDValue.buildObject()
          .put(DiagnosticConfigProperty.EVENTS_CAPACITY.name, 1000)
          .put(DiagnosticConfigProperty.USER_KEYS_CAPACITY.name, 2000)
          .put(DiagnosticConfigProperty.DATA_STORE_TYPE.name, "custom")
          .build()
        ));
  }
  
  @Test
  public void createsDiagnosticStatisticsEvent() {
    DiagnosticStore store = makeSimpleStore();
    long startDate = store.getDataSinceDate();
    DiagnosticEvent.Statistics diagnosticStatisticsEvent = store.createEventAndReset(10, 15);
    assertSame(store.getDiagnosticId(), diagnosticStatisticsEvent.id);
    assertEquals(10, diagnosticStatisticsEvent.droppedEvents);
    assertEquals(15, diagnosticStatisticsEvent.deduplicatedUsers);
    assertEquals(0, diagnosticStatisticsEvent.eventsInLastBatch);
    assertEquals(startDate, diagnosticStatisticsEvent.dataSinceDate);
  }

  @Test
  public void canRecordStreamInit() {
    DiagnosticStore store = makeSimpleStore();
    store.recordStreamInit(1000, 200, false);
    DiagnosticEvent.Statistics statsEvent = store.createEventAndReset(0, 0);
    assertEquals(1, statsEvent.streamInits.size());
    assertEquals(1000, statsEvent.streamInits.get(0).timestamp);
    assertEquals(200, statsEvent.streamInits.get(0).durationMillis);
    assertEquals(false, statsEvent.streamInits.get(0).failed);
  }

  @Test
  public void canRecordEventsInBatch() {
    DiagnosticStore store = makeSimpleStore();
    store.recordEventsInBatch(100);
    DiagnosticEvent.Statistics statsEvent = store.createEventAndReset(0, 0);
    assertEquals(100, statsEvent.eventsInLastBatch);
  }

  @Test
  public void resetsStatsOnCreate() throws InterruptedException {
    DiagnosticStore store = makeSimpleStore();
    store.recordStreamInit(1000, 200, false);
    store.recordEventsInBatch(100);
    long startDate = store.getDataSinceDate();
    Thread.sleep(2); // so that dataSinceDate will be different
    store.createEventAndReset(0, 0);
    assertNotEquals(startDate, store.getDataSinceDate());
    DiagnosticEvent.Statistics resetEvent = store.createEventAndReset(0,0);
    assertEquals(0, resetEvent.streamInits.size());
    assertEquals(0, resetEvent.eventsInLastBatch);
  }
  
  private static DiagnosticStore makeSimpleStore() {
    return new DiagnosticStore(new SdkDiagnosticParams(SDK_KEY, SDK_NAME, SDK_VERSION, PLATFORM_NAME, null, null, null));
  }
}
