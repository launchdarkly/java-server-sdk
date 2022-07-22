package com.launchdarkly.sdk.internal.events;

import com.launchdarkly.sdk.LDValue;

import java.util.List;

public class DiagnosticEvent {
  final String kind;
  final long creationDate;
  final DiagnosticId id;

  DiagnosticEvent(String kind, long creationDate, DiagnosticId id) {
    this.kind = kind;
    this.creationDate = creationDate;
    this.id = id;
  }

  public static class StreamInit {
    public final long timestamp;
    public final long durationMillis;
    public final boolean failed;

    StreamInit(long timestamp, long durationMillis, boolean failed) {
      this.timestamp = timestamp;
      this.durationMillis = durationMillis;
      this.failed = failed;
    }
  }

  public static class Statistics extends DiagnosticEvent {
    public final long dataSinceDate;
    public final long droppedEvents;
    public final long deduplicatedUsers;
    public final long eventsInLastBatch;
    public final List<StreamInit> streamInits;

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

  public static class Init extends DiagnosticEvent {
    public final LDValue sdk;
    public final LDValue configuration;
    public final LDValue platform;

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
