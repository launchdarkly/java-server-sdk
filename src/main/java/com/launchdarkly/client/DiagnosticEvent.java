package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.DiagnosticDescription;
import com.launchdarkly.client.value.LDValue;
import com.launchdarkly.client.value.LDValueType;
import com.launchdarkly.client.value.ObjectBuilder;

import java.util.List;

class DiagnosticEvent {
  static enum ConfigProperty {
    ALL_ATTRIBUTES_PRIVATE("allAttributesPrivate", LDValueType.BOOLEAN),
    CUSTOM_BASE_URI("customBaseURI", LDValueType.BOOLEAN),
    CUSTOM_EVENTS_URI("customEventsURI", LDValueType.BOOLEAN),
    CUSTOM_STREAM_URI("customStreamURI", LDValueType.BOOLEAN),
    DIAGNOSTIC_RECORDING_INTERVAL_MILLIS("diagnosticRecordingIntervalMillis", LDValueType.NUMBER),
    EVENTS_CAPACITY("eventsCapacity", LDValueType.NUMBER),
    EVENTS_FLUSH_INTERVAL_MILLIS("eventsFlushIntervalMillis", LDValueType.NUMBER),
    INLINE_USERS_IN_EVENTS("inlineUsersInEvents", LDValueType.BOOLEAN),
    POLLING_INTERVAL_MILLIS("pollingIntervalMillis", LDValueType.NUMBER),
    RECONNECT_TIME_MILLIS("reconnectTimeMillis", LDValueType.NUMBER),
    SAMPLING_INTERVAL("samplingInterval", LDValueType.NUMBER),
    STREAMING_DISABLED("streamingDisabled", LDValueType.BOOLEAN),
    USER_KEYS_CAPACITY("userKeysCapacity", LDValueType.NUMBER),
    USER_KEYS_FLUSH_INTERVAL_MILLIS("userKeysFlushIntervalMillis", LDValueType.NUMBER),
    USING_RELAY_DAEMON("usingRelayDaemon", LDValueType.BOOLEAN);
    
    String name;
    LDValueType type;
    
    private ConfigProperty(String name, LDValueType type) {
      this.name = name;
      this.type = type;
    }
  }
  
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
      
      // Add the top-level properties that are not specific to a particular component type.
      builder.put("connectTimeoutMillis", config.httpConfig.connectTimeout.toMillis());
      builder.put("socketTimeoutMillis", config.httpConfig.socketTimeout.toMillis());
      builder.put("usingProxy", config.httpConfig.proxy != null);
      builder.put("usingProxyAuthenticator", config.httpConfig.proxyAuthenticator != null);
      builder.put("offline", config.offline);
      builder.put("startWaitMillis", config.startWait.toMillis());
      
      // Allow each pluggable component to describe its own relevant properties. 
      mergeComponentProperties(builder,
          config.dataStoreFactory == null ? Components.inMemoryDataStore() : config.dataStoreFactory,
          config, "dataStoreType");
      mergeComponentProperties(builder,
          config.dataSourceFactory == null ? Components.streamingDataSource() : config.dataSourceFactory,
          config, null);
      mergeComponentProperties(builder,
          config.eventProcessorFactory == null ? Components.sendEvents() : config.eventProcessorFactory,
          config, null);
      return builder.build();
    }
    
    // Attempts to add relevant configuration properties, if any, from a customizable component:
    // - If the component does not implement DiagnosticDescription, set the defaultPropertyName property to its class name.
    // - If it does implement DiagnosticDescription, call its describeConfiguration() method to get a value.
    // - If the value is a string, then set the defaultPropertyName property to that value.
    // - If the value is an object, then copy all of its properties as long as they are ones we recognize
    //   and have the expected type.
    private static void mergeComponentProperties(ObjectBuilder builder, Object component, LDConfig config, String defaultPropertyName) {
      if (component == null) {
        return;
      }
      if (!(component instanceof DiagnosticDescription)) {
        if (defaultPropertyName != null) {
          builder.put(defaultPropertyName, LDValue.of(component.getClass().getSimpleName()));
        }
        return;
      }
      LDValue componentDesc = ((DiagnosticDescription)component).describeConfiguration(config);
      if (componentDesc == null || componentDesc.isNull()) {
        return;
      }
      if (componentDesc.isString() && defaultPropertyName != null) {
        builder.put(defaultPropertyName, componentDesc);
      } else if (componentDesc.getType() == LDValueType.OBJECT) {
        for (String key: componentDesc.keys()) {
          for (ConfigProperty prop: ConfigProperty.values()) {
            if (prop.name.equals(key)) {
              LDValue value = componentDesc.get(key);
              if (value.isNull() || value.getType() == prop.type) {
                builder.put(key, value);
              }
            }
          }
        }
      }
    }
    
    static class DiagnosticSdk {
      final String name = "java-server-sdk";
      final String version = LDClient.CLIENT_VERSION;
      final String wrapperName;
      final String wrapperVersion;

      DiagnosticSdk(LDConfig config) {
        this.wrapperName = config.httpConfig.wrapperName;
        this.wrapperVersion = config.httpConfig.wrapperVersion;
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
