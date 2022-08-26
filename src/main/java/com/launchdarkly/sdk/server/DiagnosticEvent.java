package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDValue;

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
    final LDValue sdk;
    final LDValue configuration;
    final LDValue platform;

    Init(
        long creationDate,
        DiagnosticId diagnosticId,
        LDValue sdk,
        LDValue configuration,
        LDValue platform
        ) {
      super("diagnostic-init", creationDate, diagnosticId);
      this.sdk = sdk;
      this.configuration = configuration;
      this.platform = platform;
    }
  }
}
