package com.launchdarkly.client;

import java.net.URI;

class DiagnosticEvent {

  final String kind;
  final long creationDate;
  final DiagnosticId id;

  DiagnosticEvent(String kind, long creationDate, DiagnosticId id) {
    this.kind = kind;
    this.creationDate = creationDate;
    this.id = id;
  }

  static class Statistics extends DiagnosticEvent {

    final long dataSinceDate;
    final long droppedEvents;
    final long deduplicatedUsers;
    final long eventsInQueue;

    Statistics(long creationDate, DiagnosticId id, long dataSinceDate, long droppedEvents, long deduplicatedUsers,
      long eventsInQueue) {
      super("diagnostic", creationDate, id);
      this.dataSinceDate = dataSinceDate;
      this.droppedEvents = droppedEvents;
      this.deduplicatedUsers = deduplicatedUsers;
      this.eventsInQueue = eventsInQueue;
    }
  }

  static class Init extends DiagnosticEvent {

    final DiagnosticSdk sdk;
    final DiagnosticConfiguration configuration;
    final DiagnosticPlatform platform = new DiagnosticPlatform();

    Init(long creationDate, DiagnosticId diagnosticId, LDConfig config) {
      super("diagnostic-init", creationDate, diagnosticId);
      this.sdk = new DiagnosticSdk(config);
      this.configuration = new DiagnosticConfiguration(config);
    }

    static class DiagnosticConfiguration {
      private final URI baseURI;
      private final URI eventsURI;
      private final URI streamURI;
      private final int eventsCapacity;
      private final int connectTimeoutMillis;
      private final int socketTimeoutMillis;
      private final long eventsFlushIntervalMillis;
      private final boolean usingProxy;
      private final boolean usingProxyAuthenticator;
      private final boolean streamingDisabled;
      private final boolean usingRelayDaemon;
      private final boolean offline;
      private final boolean allAttributesPrivate;
      private final boolean eventReportingDisabled;
      private final long pollingIntervalMillis;
      private final long startWaitMillis;
      private final int samplingInterval;
      private final long reconnectTimeMillis;
      private final int userKeysCapacity;
      private final long userKeysFlushIntervalMillis;
      private final boolean inlineUsersInEvents;
      private final int diagnosticRecordingIntervalMillis;
      private final String featureStore;

      DiagnosticConfiguration(LDConfig config) {
        this.baseURI = config.baseURI;
        this.eventsURI = config.eventsURI;
        this.streamURI = config.streamURI;
        this.eventsCapacity = config.capacity;
        this.connectTimeoutMillis = config.connectTimeoutMillis;
        this.socketTimeoutMillis = config.socketTimeoutMillis;
        this.eventsFlushIntervalMillis = config.flushInterval * 1000;
        this.usingProxy = config.proxy != null;
        this.usingProxyAuthenticator = config.proxyAuthenticator != null;
        this.streamingDisabled = !config.stream;
        this.usingRelayDaemon = config.useLdd;
        this.offline = config.offline;
        this.allAttributesPrivate = config.allAttributesPrivate;
        this.eventReportingDisabled = !config.sendEvents;
        this.pollingIntervalMillis = config.pollingIntervalMillis;
        this.startWaitMillis = config.startWaitMillis;
        this.samplingInterval = config.samplingInterval;
        this.reconnectTimeMillis = config.reconnectTimeMs;
        this.userKeysCapacity = config.userKeysCapacity;
        this.userKeysFlushIntervalMillis = config.userKeysFlushInterval * 1000;
        this.inlineUsersInEvents = config.inlineUsersInEvents;
        this.diagnosticRecordingIntervalMillis = config.diagnosticRecordingIntervalMillis;
        if (config.deprecatedFeatureStore != null) {
          this.featureStore = config.deprecatedFeatureStore.getClass().getSimpleName();
        } else if (config.featureStoreFactory != null) {
          this.featureStore = config.featureStoreFactory.getClass().getSimpleName();
        } else {
          this.featureStore = null;
        }
      }
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

    static class DiagnosticPlatform {
      private final String name = "Java";
      private final String javaVendor = System.getProperty("java.vendor");
      private final String javaVersion = System.getProperty("java.version");
      private final String osArch = System.getProperty("os.arch");
      private final String osName = System.getProperty("os.name");
      private final String osVersion = System.getProperty("os.version");

      DiagnosticPlatform() {
      }
    }
  }
}
