package com.launchdarkly.client;

class DiagnosticAccumulator {

  final DiagnosticId diagnosticId;
  volatile long dataSinceDate;

  DiagnosticAccumulator(DiagnosticId diagnosticId) {
    this.diagnosticId = diagnosticId;
    this.dataSinceDate = System.currentTimeMillis();
  }

  DiagnosticEvent.Statistics createEventAndReset(long droppedEvents, long deduplicatedUsers, long eventsInQueue) {
    long currentTime = System.currentTimeMillis();
    DiagnosticEvent.Statistics res = new DiagnosticEvent.Statistics(currentTime, diagnosticId, dataSinceDate, droppedEvents,
        deduplicatedUsers, eventsInQueue);
    dataSinceDate = currentTime;
    return res;
  }
}
