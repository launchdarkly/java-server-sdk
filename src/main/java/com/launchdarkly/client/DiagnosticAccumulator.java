package com.launchdarkly.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class DiagnosticAccumulator {

  final DiagnosticId diagnosticId;
  volatile long dataSinceDate;
  private final AtomicInteger eventsInLastBatch = new AtomicInteger(0);
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

  void recordEventsInBatch(int eventsInBatch) {
    eventsInLastBatch.set(eventsInBatch);
  }

  DiagnosticEvent.Statistics createEventAndReset(long droppedEvents, long deduplicatedUsers) {
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
