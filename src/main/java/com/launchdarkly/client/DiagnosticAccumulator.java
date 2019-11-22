package com.launchdarkly.client;

import java.util.ArrayList;
import java.util.List;

class DiagnosticAccumulator {

  final DiagnosticId diagnosticId;
  volatile long dataSinceDate;
  private final Object streamInitsLock = new Object();
  private ArrayList<DiagnosticEvent.StreamInit> streamInits = new ArrayList<>();

  DiagnosticAccumulator(DiagnosticId diagnosticId) {
    this.diagnosticId = diagnosticId;
    this.dataSinceDate = System.currentTimeMillis();
  }

  void recordStreamInit(long timestamp, long durationMillis, boolean failed) {
    synchronized (streamInitsLock) {
      streamInits.add(new DiagnosticEvent.StreamInit(timestamp, durationMillis, failed));
    }
  }

  DiagnosticEvent.Statistics createEventAndReset(long droppedEvents, long deduplicatedUsers, long eventsInQueue) {
    long currentTime = System.currentTimeMillis();
    List<DiagnosticEvent.StreamInit> eventInits;
    synchronized (streamInitsLock) {
      eventInits = streamInits;
      streamInits = new ArrayList<>();
    }
    DiagnosticEvent.Statistics res = new DiagnosticEvent.Statistics(currentTime, diagnosticId, dataSinceDate, droppedEvents,
        deduplicatedUsers, eventsInQueue, eventInits);
    dataSinceDate = currentTime;
    return res;
  }
}
