package com.launchdarkly.sdk.internal.events;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.ObjectBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

/**
 * Implementation of basic diagnostic event creation. Platform-specific details are provided in
 * SdkDiagnosticParams.
 */
public final class DiagnosticStore {
  private final DiagnosticId diagnosticId;
  private final long creationDate;
  private final SdkDiagnosticParams diagnosticParams;
  
  private volatile long dataSinceDate;
  private final AtomicInteger eventsInLastBatch = new AtomicInteger(0);
  private final Object streamInitsLock = new Object();
  private ArrayList<DiagnosticEvent.StreamInit> streamInits = new ArrayList<>();
  
  /**
   * Parameters for creating a DiagnosticStore.
   */
  public static class SdkDiagnosticParams {
    final String sdkKeyOrMobileKey;
    final String sdkName;
    final String sdkVersion;
    final String platformName;
    final LDValue extraPlatformData;
    final Map<String, String> defaultHttpHeaders;
    final List<LDValue> configProperties;
    
    /**
     * Creates an instance.
     * 
     * @param sdkKeyOrMobileKey the SDK key or mobile key
     * @param sdkName the SDK name as represented in diagnostic events
     * @param sdkVersion the version string
     * @param platformName the platform name as represented in diagnostic events
     * @param extraPlatformData optional JSON object for platform properties
     * @param defaultHttpHeaders from the HTTP configuration (we get the wrapper name from this)
     * @param configProperties optional JSON object for any additional config properties
     */
    public SdkDiagnosticParams(
        String sdkKeyOrMobileKey,
        String sdkName,
        String sdkVersion,
        String platformName,
        LDValue extraPlatformData,
        Map<String, String> defaultHttpHeaders,
        List<LDValue> configProperties
        ) {
      this.sdkKeyOrMobileKey = sdkKeyOrMobileKey;
      this.sdkName = sdkName;
      this.sdkVersion = sdkVersion;
      this.platformName = platformName;
      this.extraPlatformData = extraPlatformData;
      this.defaultHttpHeaders = defaultHttpHeaders == null ? emptyMap() : new HashMap<>(defaultHttpHeaders);
      this.configProperties = configProperties == null ? emptyList() : new ArrayList<LDValue>(configProperties);
    }
  }
  
  /**
   * Constructs an instance.
   * 
   * @param params the diagnostic properties
   */
  public DiagnosticStore(SdkDiagnosticParams params) {
    this.creationDate = this.dataSinceDate = System.currentTimeMillis();
    this.diagnosticId = new DiagnosticId(params.sdkKeyOrMobileKey);
    this.diagnosticParams = params;
  }
  
  /**
   * Returns the unique diagnostic identifier.
   * 
   * @return the identifier
   */
  public DiagnosticId getDiagnosticId() {
    return diagnosticId;
  }
  
  /**
   * Returns the millisecond timestamp when the current diagnostic stats began.
   * 
   * @return the timestamp
   */
  public long getDataSinceDate() {
    return dataSinceDate;
  }
  
  /**
   * Returns the initial diagnostic event.
   * 
   * @return the initial event
   */
  public DiagnosticEvent.Init getInitEvent() {
    return new DiagnosticEvent.Init(creationDate, diagnosticId,
        makeInitEventSdkData(), makeInitEventConfigData(), makeInitEventPlatformData());
  }

  private LDValue makeInitEventSdkData() {
    ObjectBuilder b = LDValue.buildObject()
        .put("name", diagnosticParams.sdkName)
        .put("version", diagnosticParams.sdkVersion);
    for (Map.Entry<String, String> kv: diagnosticParams.defaultHttpHeaders.entrySet()) {
        if (kv.getKey().equalsIgnoreCase("x-launchdarkly-wrapper")) {
            if (kv.getValue().contains("/")) {
                b.put("wrapperName", kv.getValue().substring(0, kv.getValue().indexOf("/")));
                b.put("wrapperVersion", kv.getValue().substring(kv.getValue().indexOf("/") + 1));
            } else {
                b.put("wrapperName", kv.getValue());
            }
        }
    }
    return b.build();
  }
  
  private LDValue makeInitEventConfigData() {
    ObjectBuilder b = LDValue.buildObject();
    for (LDValue configProps: diagnosticParams.configProperties) {
      if (configProps == null || configProps.getType() != LDValueType.OBJECT) {
        continue;
      }
      for (String prop: configProps.keys()) {
        // filter this to make sure a badly-behaved custom component doesn't inject weird
        // properties that will confuse the event recorder
        for (DiagnosticConfigProperty p: DiagnosticConfigProperty.values()) {
          if (p.name.equals(prop)) {
            LDValue value = configProps.get(prop);
            if (value.getType() == p.type) {
              b.put(prop, value);
            }
            break;
          }
        }
      }
    }
    return b.build();
  }
  
  private LDValue makeInitEventPlatformData() {
    ObjectBuilder b = LDValue.buildObject()
        .put("name", diagnosticParams.platformName)
        .put("osArch", System.getProperty("os.arch"))
        .put("osVersion", System.getProperty("os.version"));
    if (diagnosticParams.extraPlatformData != null) {
      for (String key: diagnosticParams.extraPlatformData.keys()) {
        b.put(key, diagnosticParams.extraPlatformData.get(key));
      }
    }
    return b.build();
  }
  
  /**
   * Records a successful or failed stream initialization.
   * 
   * @param timestamp the millisecond timestamp
   * @param durationMillis how long the initialization took
   * @param failed true if failed
   */
  public void recordStreamInit(long timestamp, long durationMillis, boolean failed) {
    synchronized (streamInitsLock) {
      streamInits.add(new DiagnosticEvent.StreamInit(timestamp, durationMillis, failed));
    }
  }

  /**
   * Records the number of events in the last flush payload.
   * 
   * @param eventsInBatch the event count
   */
  public void recordEventsInBatch(int eventsInBatch) {
    eventsInLastBatch.set(eventsInBatch);
  }

  /**
   * Creates a statistics event and then resets the counters.
   * 
   * @param droppedEvents number of dropped events
   * @param deduplicatedContexts number of deduplicated contexts
   * @return the event
   */
  public DiagnosticEvent.Statistics createEventAndReset(long droppedEvents, long deduplicatedContexts) {
    long currentTime = System.currentTimeMillis();
    List<DiagnosticEvent.StreamInit> eventInits;
    synchronized (streamInitsLock) {
      eventInits = streamInits;
      streamInits = new ArrayList<>();
    }
    long eventsInBatch = eventsInLastBatch.getAndSet(0);
    DiagnosticEvent.Statistics res = new DiagnosticEvent.Statistics(currentTime, diagnosticId, dataSinceDate, droppedEvents,
        deduplicatedContexts, eventsInBatch, eventInits);
    dataSinceDate = currentTime;
    return res;
  }
}
