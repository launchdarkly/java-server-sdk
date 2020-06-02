package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.server.interfaces.BasicConfiguration;
import com.launchdarkly.sdk.server.interfaces.DiagnosticDescription;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import java.util.List;
import java.util.Map;

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

    Init(
        long creationDate,
        DiagnosticId diagnosticId,
        LDConfig config,
        BasicConfiguration basicConfig,
        HttpConfiguration httpConfig
        ) {
      super("diagnostic-init", creationDate, diagnosticId);
      this.sdk = new DiagnosticSdk(httpConfig);
      this.configuration = getConfigurationData(config, basicConfig, httpConfig);
    }

    static LDValue getConfigurationData(LDConfig config, BasicConfiguration basicConfig, HttpConfiguration httpConfig) {
      ObjectBuilder builder = LDValue.buildObject();
      
      // Add the top-level properties that are not specific to a particular component type.
      builder.put("connectTimeoutMillis", httpConfig.getConnectTimeout().toMillis());
      builder.put("socketTimeoutMillis", httpConfig.getSocketTimeout().toMillis());
      builder.put("usingProxy", httpConfig.getProxy() != null);
      builder.put("usingProxyAuthenticator", httpConfig.getProxyAuthentication() != null);
      builder.put("startWaitMillis", config.startWait.toMillis());
      
      // Allow each pluggable component to describe its own relevant properties. 
      mergeComponentProperties(builder, config.dataStoreFactory, basicConfig, "dataStoreType");
      mergeComponentProperties(builder, config.dataSourceFactory, basicConfig, null);
      mergeComponentProperties(builder, config.eventProcessorFactory, basicConfig, null);
      return builder.build();
    }
    
    // Attempts to add relevant configuration properties, if any, from a customizable component:
    // - If the component does not implement DiagnosticDescription, set the defaultPropertyName property to "custom".
    // - If it does implement DiagnosticDescription, call its describeConfiguration() method to get a value.
    // - If the value is a string, then set the defaultPropertyName property to that value.
    // - If the value is an object, then copy all of its properties as long as they are ones we recognize
    //   and have the expected type.
    private static void mergeComponentProperties(
        ObjectBuilder builder,
        Object component,
        BasicConfiguration basicConfig,
        String defaultPropertyName
        ) {
      if (!(component instanceof DiagnosticDescription)) {
        if (defaultPropertyName != null) {
          builder.put(defaultPropertyName, "custom");
        }
        return;
      }
      LDValue componentDesc = LDValue.normalize(((DiagnosticDescription)component).describeConfiguration(basicConfig));
      if (defaultPropertyName != null) {
        builder.put(defaultPropertyName, componentDesc.isString() ? componentDesc.stringValue() : "custom");
      } else if (componentDesc.getType() == LDValueType.OBJECT) {
        for (String key: componentDesc.keys()) {
          for (ConfigProperty prop: ConfigProperty.values()) {
            if (prop.name.equals(key)) {
              LDValue value = componentDesc.get(key);
              if (value.getType() == prop.type) {
                builder.put(key, value);
              }
            }
          }
        }
      }
    }
    
    static class DiagnosticSdk {
      final String name = "java-server-sdk";
      final String version = Version.SDK_VERSION;
      final String wrapperName;
      final String wrapperVersion;

      DiagnosticSdk(HttpConfiguration httpConfig) {
        for (Map.Entry<String, String> headers: httpConfig.getDefaultHeaders()) {
          if (headers.getKey().equalsIgnoreCase("X-LaunchDarkly-Wrapper") ) {
            String id = headers.getValue();
            if (id.indexOf("/") >= 0) {
              this.wrapperName = id.substring(0, id.indexOf("/"));
              this.wrapperVersion = id.substring(id.indexOf("/") + 1);
            } else {
              this.wrapperName = id;
              this.wrapperVersion = null;
            }
            return;
          }
        }
        this.wrapperName = null;
        this.wrapperVersion = null;
      }
    }

    @SuppressWarnings("unused") // fields are for JSON serialization only
    static class DiagnosticPlatform {
      private final String name = "Java";
      private final String javaVendor = System.getProperty("java.vendor");
      private final String javaVersion = System.getProperty("java.version");
      private final String osArch = System.getProperty("os.arch");
      final String osName = normalizeOsName(System.getProperty("os.name")); // visible for tests
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
