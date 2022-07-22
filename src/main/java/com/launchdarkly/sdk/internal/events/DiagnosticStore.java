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
  
  public static class SdkDiagnosticParams {
    final String sdkKeyOrMobileKey;
    final String sdkName;
    final String sdkVersion;
    final String platformName;
    final LDValue extraPlatformData;
    final Map<String, String> defaultHttpHeaders;
    final List<LDValue> configProperties;
    
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
  
  public DiagnosticStore(SdkDiagnosticParams params) {
    this.creationDate = this.dataSinceDate = System.currentTimeMillis();
    this.diagnosticId = new DiagnosticId(params.sdkKeyOrMobileKey);
    this.diagnosticParams = params;
  }
  
  public DiagnosticId getDiagnosticId() {
    return diagnosticId;
  }
  
  public long getDataSinceDate() {
    return dataSinceDate;
  }
  
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
  
  public void recordStreamInit(long timestamp, long durationMillis, boolean failed) {
    synchronized (streamInitsLock) {
      streamInits.add(new DiagnosticEvent.StreamInit(timestamp, durationMillis, failed));
    }
  }

  public void recordEventsInBatch(int eventsInBatch) {
    eventsInLastBatch.set(eventsInBatch);
  }

  public DiagnosticEvent.Statistics createEventAndReset(long droppedEvents, long deduplicatedUsers) {
    long currentTime = System.currentTimeMillis();
    List<DiagnosticEvent.StreamInit> eventInits;
    synchronized (streamInitsLock) {
      eventInits = streamInits;
      streamInits = new ArrayList<>();
    }
    long eventsInBatch = eventsInLastBatch.getAndSet(0);
    DiagnosticEvent.Statistics res = new DiagnosticEvent.Statistics(currentTime, diagnosticId, dataSinceDate, droppedEvents,
        deduplicatedUsers, eventsInBatch, eventInits);
    dataSinceDate = currentTime;
    return res;
  }
}
