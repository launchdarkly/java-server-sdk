package com.launchdarkly.client;

class DiagnosticAccumulator {

  volatile long dataSinceDate;
  volatile DiagnosticId diagnosticId;

  void start(DiagnosticId diagnosticId, long dataSinceDate) {
    this.diagnosticId = diagnosticId;
    this.dataSinceDate = dataSinceDate;
  }

  DiagnosticEvent.Statistics createEventAndReset(long droppedEvents, long deduplicatedUsers, long eventsInQueue) {
    long currentTime = System.currentTimeMillis();
    DiagnosticEvent.Statistics res = new DiagnosticEvent.Statistics(currentTime, diagnosticId, dataSinceDate, droppedEvents,
        deduplicatedUsers, eventsInQueue);
    dataSinceDate = currentTime;
    return res;
  }
}
