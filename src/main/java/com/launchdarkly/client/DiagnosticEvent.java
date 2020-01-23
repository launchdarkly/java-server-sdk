package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.DiagnosticDescription;
import com.launchdarkly.client.value.LDValue;
import com.launchdarkly.client.value.ObjectBuilder;

import java.util.List;

class DiagnosticEvent {
  final String kind;
  final long creationDate;
  final DiagnosticId id;

  DiagnosticEvent(String kind, long creationDate, DiagnosticId id) {
    this.kind = kind;
    this.creationDate = creationDate;
    this.id = id;
  }

  static class StreamInit {
    long timestamp;
    long durationMillis;
    boolean failed;

    StreamInit(long timestamp, long durationMillis, boolean failed) {
      this.timestamp = timestamp;
      this.durationMillis = durationMillis;
      this.failed = failed;
    }
  }

  static class Statistics extends DiagnosticEvent {

    final long dataSinceDate;
    final long droppedEvents;
    final long deduplicatedUsers;
    final long eventsInLastBatch;
    final List<StreamInit> streamInits;

    Statistics(long creationDate, DiagnosticId id, long dataSinceDate, long droppedEvents, long deduplicatedUsers,
      long eventsInLastBatch, List<StreamInit> streamInits) {
      super("diagnostic", creationDate, id);
      this.dataSinceDate = dataSinceDate;
      this.droppedEvents = droppedEvents;
      this.deduplicatedUsers = deduplicatedUsers;
      this.eventsInLastBatch = eventsInLastBatch;
      this.streamInits = streamInits;
    }
  }

  static class Init extends DiagnosticEvent {
    // Arbitrary limit to prevent custom components from injecting too much data into the configuration object
    private static final int MAX_COMPONENT_PROPERTY_LENGTH = 100;
    
    final DiagnosticSdk sdk;
    final LDValue configuration;
    final DiagnosticPlatform platform = new DiagnosticPlatform();

    Init(long creationDate, DiagnosticId diagnosticId, LDConfig config) {
      super("diagnostic-init", creationDate, diagnosticId);
      this.sdk = new DiagnosticSdk(config);
      this.configuration = getConfigurationData(config);
    }

    static LDValue getConfigurationData(LDConfig config) {
      ObjectBuilder builder = LDValue.buildObject();
      builder.put("customBaseURI", !(LDConfig.DEFAULT_BASE_URI.equals(config.baseURI)));
      builder.put("customEventsURI", !(LDConfig.DEFAULT_EVENTS_URI.equals(config.eventsURI)));
      builder.put("customStreamURI", !(LDConfig.DEFAULT_STREAM_URI.equals(config.streamURI)));
      builder.put("eventsCapacity", config.capacity);
      builder.put("connectTimeoutMillis", config.connectTimeoutUnit.toMillis(config.connectTimeout));
      builder.put("socketTimeoutMillis", config.socketTimeoutUnit.toMillis(config.socketTimeout));
      builder.put("eventsFlushIntervalMillis", config.flushInterval * 1000);
      builder.put("usingProxy", config.proxy != null);
      builder.put("usingProxyAuthenticator", config.proxyAuthenticator != null);
      builder.put("streamingDisabled", !config.stream);
      builder.put("usingRelayDaemon", config.useLdd);
      builder.put("offline", config.offline);
      builder.put("allAttributesPrivate", config.allAttributesPrivate);
      builder.put("pollingIntervalMillis", config.pollingIntervalMillis);
      builder.put("startWaitMillis", config.startWaitMillis);
      builder.put("samplingInterval", config.samplingInterval);
      builder.put("reconnectTimeMillis", config.reconnectTimeMs);
      builder.put("userKeysCapacity", config.userKeysCapacity);
      builder.put("userKeysFlushIntervalMillis", config.userKeysFlushInterval * 1000);
      builder.put("inlineUsersInEvents", config.inlineUsersInEvents);
      builder.put("diagnosticRecordingIntervalMillis", config.diagnosticRecordingIntervalMillis);
      mergeComponentProperties(builder, config.deprecatedFeatureStore, "dataStore");
      mergeComponentProperties(builder, config.dataStoreFactory, "dataStore");
      return builder.build();
    }
    
    // Attempts to add relevant configuration properties, if any, from a customizable component:
    // - If the component does not implement DiagnosticDescription, set the defaultPropertyName property to its class name.
    // - If it does implement DiagnosticDescription, call its describeConfiguration() method to get a value.
    //   Currently the only supported value is a string; the defaultPropertyName property will be set to this.
    //   In the future, we will support JSON objects so that our own components can report properties that are
    //   not in LDConfig.
    private static void mergeComponentProperties(ObjectBuilder builder, Object component, String defaultPropertyName) {
      if (component == null) {
        return;
      }
      if (!(component instanceof DiagnosticDescription)) {
        if (defaultPropertyName != null) {
          builder.put(defaultPropertyName, validateSimpleValue(LDValue.of(component.getClass().getSimpleName())));
        }
        return;
      }
      LDValue componentDesc = validateSimpleValue(((DiagnosticDescription)component).describeConfiguration());
      if (!componentDesc.isNull() && defaultPropertyName != null) {
        builder.put(defaultPropertyName, componentDesc);
      }
    }
    
    private static LDValue validateSimpleValue(LDValue value) {
      if (value != null && value.isString()) {
        if (value.stringValue().length() > MAX_COMPONENT_PROPERTY_LENGTH) {
          return LDValue.of(value.stringValue().substring(0, MAX_COMPONENT_PROPERTY_LENGTH)); 
        }
        return value;
      }
      return LDValue.ofNull();
    }
    
    static class DiagnosticSdk {
      final String name = "java-server-sdk";
      final String version = LDClient.CLIENT_VERSION;
      final String wrapperName;
      final String wrapperVersion;

      DiagnosticSdk(LDConfig config) {
        this.wrapperName = config.wrapperName;
        this.wrapperVersion = config.wrapperVersion;
      }
    }

    @SuppressWarnings("unused") // fields are for JSON serialization only
    static class DiagnosticPlatform {
      private final String name = "Java";
      private final String javaVendor = System.getProperty("java.vendor");
      private final String javaVersion = System.getProperty("java.version");
      private final String osArch = System.getProperty("os.arch");
      private final String osName = normalizeOsName(System.getProperty("os.name"));
      private final String osVersion = System.getProperty("os.version");

      DiagnosticPlatform() {
      }
      
      private static String normalizeOsName(String osName) {
        // For our diagnostics data, we prefer the standard names "Linux", "MacOS", and "Windows".
        // "Linux" is already what the JRE returns in Linux. In Windows, we get "Windows 10" etc.
        if (osName != null) {
          if (osName.equals("Mac OS X")) {
            return "MacOS";
          }
          if (osName.startsWith("Windows")) {
            return "Windows";
          }
        }
        return osName;
      }
    }
  }
}
