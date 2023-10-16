package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogLevel;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogCapture;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.internal.events.Event;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MigrationOpTrackerTests {
  private final LogCapture logCapture = Logs.capture();
  private final LDLogger testLogger = LDLogger.withAdapter(logCapture, "");

  @Test
  public void itCanMakeAMinimalEvent() {
    EvaluationDetail<String> detail = getDetail();
    LDContext context = LDContext.create("user-key");

    MigrationOpTracker tracker = basicTracker(detail, context);

    tracker.op(MigrationOp.READ);
    tracker.invoked(MigrationOrigin.OLD);

    Optional<Event.MigrationOp> event = tracker.createEvent();
    assertTrue(event.isPresent());
    Event.MigrationOp me = event.get();
    assertEquals(MigrationOp.READ.toString(), me.getOperation());
    assertEquals("test-key", me.getFeatureKey());
    assertEquals(detail.getReason(), me.getReason());
    assertEquals(detail.getValue(), me.getValue().stringValue());
    assertEquals(MigrationStage.LIVE.toString(), me.getValue().stringValue());
    assertEquals(MigrationStage.OFF.toString(), me.getDefaultVal().stringValue());
    assertEquals(context, me.getContext());

    assertTrue(me.getInvokedMeasurement().wasOldInvoked());
    assertFalse(me.getInvokedMeasurement().wasNewInvoked());
    assertNull(me.getConsistencyMeasurement());
    assertNull(me.getErrorMeasurement());
    assertNull(me.getLatencyMeasurement());

    assertEquals(0, logCapture.getMessages().size());
  }

  @Test
  public void itDoesNotMakeAnEventIfTheFlagKeyIsEmpty() {
    EvaluationDetail<String> detail = getDetail();
    LDContext context = LDContext.create("user-key");

    MigrationOpTracker tracker = new MigrationOpTracker(
      "",
      null,
      detail,
      MigrationStage.OFF,
      MigrationStage.LIVE,
      context,
      1,
      testLogger);

    tracker.op(MigrationOp.READ);
    tracker.invoked(MigrationOrigin.OLD);

    Optional<Event.MigrationOp> event = tracker.createEvent();
    assertFalse(event.isPresent());
  }

  @Test
  public void itCanMakeAnEventForAReadOrWriteOperation() {
    EvaluationDetail<String> detail = getDetail();
    LDContext context = LDContext.create("user-key");

    MigrationOpTracker tracker = basicTracker(detail, context);

    tracker.op(MigrationOp.WRITE);
    tracker.invoked(MigrationOrigin.NEW);

    Optional<Event.MigrationOp> event = tracker.createEvent();
    assertTrue(event.isPresent());
    Event.MigrationOp me = event.get();
    assertEquals(MigrationOp.WRITE.toString(), me.getOperation());

    MigrationOpTracker tracker2 = basicTracker(detail, context);

    tracker2.op(MigrationOp.READ);
    tracker2.invoked(MigrationOrigin.NEW);

    Optional<Event.MigrationOp> event2 = tracker2.createEvent();
    assertTrue(event2.isPresent());
    Event.MigrationOp me2 = event2.get();
    assertEquals(MigrationOp.READ.toString(), me2.getOperation());

    assertEquals(0, logCapture.getMessages().size());
  }

  @Test
  public void itMakesNoEventIfNoOperationWasSet() {
    LDContext context = LDContext.create("user-key");

    MigrationOpTracker tracker = basicTracker(getDetail(), context);
    tracker.invoked(MigrationOrigin.OLD);

    assertFalse(tracker.createEvent().isPresent());

    LogCapture.Message message = logCapture.requireMessage(LDLogLevel.ERROR, 0);
    assertEquals("The operation must be set, using \"op\" before an event can be created.", message.getText());
  }

  @NotNull
  private MigrationOpTracker basicTracker(EvaluationDetail<String> detail, LDContext context) {
    return new MigrationOpTracker(
        "test-key",
        null,
        detail,
        MigrationStage.OFF,
        MigrationStage.LIVE,
        context,
        1,
        testLogger);
  }

  @NotNull
  private MigrationOpTracker trackerWithFlag(EvaluationDetail<String> detail, LDContext context) {
    return new MigrationOpTracker(
      "test-key",
      new DataModel.FeatureFlag("flag", 2, true, null,
        "salt", null, null, null, null, 0, null,
        false, false, false, null, false,
        5l, null, false),
      detail,
      MigrationStage.OFF,
      MigrationStage.LIVE,
      context,
      1,
      testLogger);
  }

  @Test
  public void itMakesNoEventIfNoOriginsWereInvoked() {
    LDContext context = LDContext.create("user-key");

    MigrationOpTracker tracker = basicTracker(getDetail(), context);

    tracker.op(MigrationOp.READ);

    assertFalse(tracker.createEvent().isPresent());
    LogCapture.Message message = logCapture.requireMessage(LDLogLevel.ERROR, 0);
    assertEquals("The migration invoked neither the \"old\" or \"new\" implementation and an event " +
        "cannot be generated.", message.getText());
  }

  @Test
  public void itMakesNoEventIfTheContextIsInvalid() {
    EvaluationDetail<String> detail = getDetail();
    LDContext context = LDContext.create(ContextKind.of("kind"), "kind-key");

    MigrationOpTracker tracker = basicTracker(detail, context);

    tracker.op(MigrationOp.READ);
    tracker.invoked(MigrationOrigin.OLD);

    assertFalse(tracker.createEvent().isPresent());
    LogCapture.Message message = logCapture.requireMessage(LDLogLevel.ERROR, 0);
    assertEquals("The migration was not done against a valid context and cannot generate an event.", message.getText());
  }

  @Test
  public void itHandlesAllPermutationsOfInvoked() {
    LDContext context = LDContext.create("user-key");

    MigrationOpTracker tracker = basicTracker(getDetail(), context);

    tracker.op(MigrationOp.READ);
    tracker.invoked(MigrationOrigin.OLD);
    Optional<Event.MigrationOp> opt1 = tracker.createEvent();
    assertTrue(opt1.isPresent());
    assertTrue(opt1.get().getInvokedMeasurement().wasOldInvoked());
    assertFalse(opt1.get().getInvokedMeasurement().wasNewInvoked());

    tracker.invoked(MigrationOrigin.NEW);
    Optional<Event.MigrationOp> opt2 = tracker.createEvent();
    assertTrue(opt2.isPresent());
    assertTrue(opt2.get().getInvokedMeasurement().wasOldInvoked());
    assertTrue(opt2.get().getInvokedMeasurement().wasNewInvoked());

    MigrationOpTracker tracker2 = basicTracker(getDetail(), context);
    tracker2.op(MigrationOp.READ);
    tracker2.invoked(MigrationOrigin.NEW);
    Optional<Event.MigrationOp> opt3 = tracker2.createEvent();
    assertTrue(opt3.isPresent());
    assertFalse(opt3.get().getInvokedMeasurement().wasOldInvoked());
    assertTrue(opt3.get().getInvokedMeasurement().wasNewInvoked());
    assertEquals(0, logCapture.getMessages().size());
  }

  private static EvaluationDetail<String> getDetail() {
    return EvaluationDetail.fromValue(
        "live", -1, EvaluationReason.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND));
  }

  @Test
  public void itHandlesAllPermutationsOfErrors() {
    LDContext context = LDContext.create("user-key");

    MigrationOpTracker tracker = basicTracker(getDetail(), context);

    tracker.op(MigrationOp.READ);
    tracker.invoked(MigrationOrigin.OLD);
    tracker.invoked(MigrationOrigin.NEW);

    Optional<Event.MigrationOp> opt1 = tracker.createEvent();
    assertTrue(opt1.isPresent());
    assertNull(opt1.get().getErrorMeasurement());

    tracker.error(MigrationOrigin.OLD);

    Optional<Event.MigrationOp> opt2 = tracker.createEvent();
    assertTrue(opt2.isPresent());
    assertNotNull(opt2.get().getErrorMeasurement());
    assertTrue(opt2.get().getErrorMeasurement().hasMeasurement());
    assertTrue(opt2.get().getErrorMeasurement().hasOldError());
    assertFalse(opt2.get().getErrorMeasurement().hasNewError());

    tracker.error(MigrationOrigin.NEW);

    Optional<Event.MigrationOp> opt3 = tracker.createEvent();
    assertTrue(opt3.isPresent());
    assertNotNull(opt3.get().getErrorMeasurement());
    assertTrue(opt3.get().getErrorMeasurement().hasMeasurement());
    assertTrue(opt3.get().getErrorMeasurement().hasOldError());
    assertTrue(opt3.get().getErrorMeasurement().hasNewError());

    MigrationOpTracker tracker2 = basicTracker(getDetail(), context);

    tracker2.op(MigrationOp.READ);
    tracker2.invoked(MigrationOrigin.NEW);
    tracker2.error(MigrationOrigin.NEW);
    Optional<Event.MigrationOp> opt4 = tracker2.createEvent();
    assertTrue(opt4.isPresent());
    assertNotNull(opt4.get().getErrorMeasurement());
    assertTrue(opt4.get().getErrorMeasurement().hasMeasurement());
    assertFalse(opt4.get().getErrorMeasurement().hasOldError());
    assertTrue(opt4.get().getErrorMeasurement().hasNewError());

    assertEquals(0, logCapture.getMessages().size());
  }

  @Test
  public void itHandlesAllPermutationsOfLatency() {
    LDContext context = LDContext.create("user-key");

    MigrationOpTracker tracker = basicTracker(getDetail(), context);

    tracker.op(MigrationOp.READ);
    tracker.invoked(MigrationOrigin.OLD);
    tracker.invoked(MigrationOrigin.NEW);

    Optional<Event.MigrationOp> opt1 = tracker.createEvent();
    assertTrue(opt1.isPresent());
    assertNull(opt1.get().getLatencyMeasurement());

    tracker.latency(MigrationOrigin.OLD, Duration.ofMillis(100));

    Optional<Event.MigrationOp> opt2 = tracker.createEvent();
    assertTrue(opt2.isPresent());
    assertNotNull(opt2.get().getLatencyMeasurement());
    assertEquals(100, opt2.get().getLatencyMeasurement().getOldLatencyMs().longValue());
    assertNull(opt2.get().getLatencyMeasurement().getNewLatencyMs());

    tracker.latency(MigrationOrigin.NEW, Duration.ofMillis(200));

    Optional<Event.MigrationOp> opt3 = tracker.createEvent();
    assertTrue(opt3.isPresent());
    assertNotNull(opt3.get().getLatencyMeasurement());
    assertEquals(100, opt3.get().getLatencyMeasurement().getOldLatencyMs().longValue());
    assertEquals(200, opt3.get().getLatencyMeasurement().getNewLatencyMs().longValue());

    MigrationOpTracker tracker2 = basicTracker(getDetail(), context);

    tracker2.op(MigrationOp.READ);
    tracker2.invoked(MigrationOrigin.OLD);
    tracker2.invoked(MigrationOrigin.NEW);

    tracker2.latency(MigrationOrigin.NEW, Duration.ofMillis(100));

    Optional<Event.MigrationOp> opt4 = tracker2.createEvent();
    assertTrue(opt4.isPresent());
    assertNotNull(opt4.get().getLatencyMeasurement());
    assertEquals(100, opt4.get().getLatencyMeasurement().getNewLatencyMs().longValue());
    assertNull(opt4.get().getLatencyMeasurement().getOldLatencyMs());

    assertEquals(0, logCapture.getMessages().size());
  }

  @Test
  public void itReportsAConsistencyErrorForLatencyWithoutInvoked() {
    LDContext context = LDContext.create("user-key");
    MigrationOpTracker tracker = basicTracker(getDetail(), context);

    tracker.op(MigrationOp.READ);

    tracker.invoked(MigrationOrigin.OLD);
    tracker.latency(MigrationOrigin.NEW, Duration.ofMillis(100));

    Optional<Event.MigrationOp> opt = tracker.createEvent();
    assertFalse(opt.isPresent());
    assertEquals("For migration op(read) flagKey(test-key): Latency was recorded for " +
        "NEW, but that origin was not invoked.", logCapture.requireMessage(LDLogLevel.ERROR, 0).getText());


    MigrationOpTracker tracker2 = basicTracker(getDetail(), context);

    tracker2.op(MigrationOp.WRITE);

    tracker2.invoked(MigrationOrigin.NEW);
    tracker2.latency(MigrationOrigin.OLD, Duration.ofMillis(100));

    Optional<Event.MigrationOp> opt2 = tracker2.createEvent();
    assertFalse(opt2.isPresent());
    assertEquals("For migration op(write) flagKey(test-key): Latency was recorded for " +
        "OLD, but that origin was not invoked.", logCapture.requireMessage(LDLogLevel.ERROR, 0).getText());
  }

  @Test
  public void itReportsAConsistencyErrorForErrorsWithoutInvoked() {
    LDContext context = LDContext.create("user-key");
    MigrationOpTracker tracker = basicTracker(getDetail(), context);

    tracker.op(MigrationOp.READ);

    tracker.invoked(MigrationOrigin.OLD);
    tracker.error(MigrationOrigin.NEW);

    Optional<Event.MigrationOp> opt = tracker.createEvent();
    assertFalse(opt.isPresent());
    assertEquals("For migration op(read) flagKey(test-key): Error reported for NEW, but that " +
        "origin was not invoked.", logCapture.requireMessage(LDLogLevel.ERROR, 0).getText());


    MigrationOpTracker tracker2 = basicTracker(getDetail(), context);

    tracker2.op(MigrationOp.WRITE);

    tracker2.invoked(MigrationOrigin.NEW);
    tracker2.error(MigrationOrigin.OLD);

    Optional<Event.MigrationOp> opt2 = tracker2.createEvent();
    assertFalse(opt2.isPresent());
    assertEquals("For migration op(write) flagKey(test-key): Error reported for OLD, but that " +
        "origin was not invoked.", logCapture.requireMessage(LDLogLevel.ERROR, 0).getText());
  }

  @Test
  public void itReportsAConsistencyErrorForComparisonWithoutBothMethods() {
    LDContext context = LDContext.create("user-key");
    MigrationOpTracker tracker = basicTracker(getDetail(), context);

    tracker.op(MigrationOp.READ);

    tracker.invoked(MigrationOrigin.OLD);
    tracker.consistency(() -> true);

    Optional<Event.MigrationOp> opt = tracker.createEvent();
    assertFalse(opt.isPresent());
    assertEquals("For migration op(read) flagKey(test-key): Consistency check" +
        " was done, but NEW was not invoked. Both \"old\" and \"new\" must be invoked to do a comparison.",
        logCapture.requireMessage(LDLogLevel.ERROR, 0).getText());


    MigrationOpTracker tracker2 = basicTracker(getDetail(), context);

    tracker2.op(MigrationOp.WRITE);

    tracker2.invoked(MigrationOrigin.NEW);
    tracker2.consistency(() -> true);

    Optional<Event.MigrationOp> opt2 = tracker2.createEvent();
    assertFalse(opt2.isPresent());
    assertEquals("For migration op(write) flagKey(test-key): Consistency check " +
        "was done, but OLD was not invoked. Both \"old\" and \"new\" must be invoked to do a comparison.",
        logCapture.requireMessage(LDLogLevel.ERROR, 0).getText());
  }

  @Test
  public void itHandlesExceptionsInTheComparisonMethod() {
    LDContext context = LDContext.create("user-key");
    MigrationOpTracker tracker = basicTracker(getDetail(), context);

    tracker.op(MigrationOp.READ);

    tracker.invoked(MigrationOrigin.OLD);
    tracker.invoked(MigrationOrigin.NEW);
    tracker.consistency(() -> {throw new RuntimeException("I HAVE FAILED");});

    Optional<Event.MigrationOp> opt = tracker.createEvent();
    assertTrue(opt.isPresent());
    assertEquals("Exception when executing consistency check function for migration 'test-key' the" +
        " consistency check will not be included in the generated migration op event. Exception:" +
        " java.lang.RuntimeException: I HAVE FAILED",
      logCapture.requireMessage(LDLogLevel.ERROR, 0).getText());

    assertNull(opt.get().getConsistencyMeasurement());
  }

  @Test
  public void itUsesTheDefaultSamplingRatioOfOne() {
    EvaluationDetail<String> detail = getDetail();
    LDContext context = LDContext.create("user-key");
    MigrationOpTracker tracker = basicTracker(detail, context);

    tracker.op(MigrationOp.READ);
    tracker.invoked(MigrationOrigin.OLD);

    Optional<Event.MigrationOp> event = tracker.createEvent();
    assertTrue(event.isPresent());
    assertEquals(1, event.get().getSamplingRatio());
  }

  @Test
  public void itUsesTheDefaultVersionOfNegativeOne() {
    EvaluationDetail<String> detail = getDetail();
    LDContext context = LDContext.create("user-key");
    MigrationOpTracker tracker = basicTracker(detail, context);

    tracker.op(MigrationOp.READ);
    tracker.invoked(MigrationOrigin.OLD);

    Optional<Event.MigrationOp> event = tracker.createEvent();
    assertTrue(event.isPresent());
    assertEquals(-1, event.get().getFlagVersion());
  }

  @Test
  public void itCanIncludeAVersionFromAFlag() {
    EvaluationDetail<String> detail = getDetail();
    LDContext context = LDContext.create("user-key");
    MigrationOpTracker tracker = trackerWithFlag(detail, context);

    tracker.op(MigrationOp.READ);
    tracker.invoked(MigrationOrigin.OLD);

    Optional<Event.MigrationOp> event = tracker.createEvent();
    assertTrue(event.isPresent());
    assertEquals(2, event.get().getFlagVersion());
  }

  @Test
  public void itCanIncludeASamplingRatioFromAFlag() {
    EvaluationDetail<String> detail = getDetail();
    LDContext context = LDContext.create("user-key");
    MigrationOpTracker tracker = trackerWithFlag(detail, context);

    tracker.op(MigrationOp.READ);
    tracker.invoked(MigrationOrigin.OLD);

    Optional<Event.MigrationOp> event = tracker.createEvent();
    assertTrue(event.isPresent());
    assertEquals(5, event.get().getSamplingRatio());
  }
}
