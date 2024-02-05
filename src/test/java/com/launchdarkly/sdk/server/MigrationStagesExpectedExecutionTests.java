package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.internal.events.Event;
import com.launchdarkly.sdk.server.migrations.Migration;
import com.launchdarkly.sdk.server.migrations.MigrationExecution;
import com.launchdarkly.sdk.server.migrations.MigrationSerialOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class MigrationStagesExpectedExecutionTests extends MigrationExecutionFixture {

  @Parameterized.Parameters(name = "{0}-{1}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][]{
        // Mode, Stage, read old, read new, write old, write new.
        {MigrationExecution.Parallel(), MigrationStage.OFF, true, false, true, false},
        {MigrationExecution.Parallel(), MigrationStage.DUAL_WRITE, true, false, true, true},
        {MigrationExecution.Parallel(), MigrationStage.SHADOW, true, true, true, true},
        {MigrationExecution.Parallel(), MigrationStage.LIVE, true, true, true, true},
        {MigrationExecution.Parallel(), MigrationStage.RAMP_DOWN, false, true, true, true},
        {MigrationExecution.Parallel(), MigrationStage.COMPLETE, false, true, false, true},

        {MigrationExecution.Serial(MigrationSerialOrder.FIXED), MigrationStage.OFF, true, false, true, false},
        {MigrationExecution.Serial(MigrationSerialOrder.FIXED), MigrationStage.DUAL_WRITE, true, false, true, true},
        {MigrationExecution.Serial(MigrationSerialOrder.FIXED), MigrationStage.SHADOW, true, true, true, true},
        {MigrationExecution.Serial(MigrationSerialOrder.FIXED), MigrationStage.LIVE, true, true, true, true},
        {MigrationExecution.Serial(MigrationSerialOrder.FIXED), MigrationStage.RAMP_DOWN, false, true, true, true},
        {MigrationExecution.Serial(MigrationSerialOrder.FIXED), MigrationStage.COMPLETE, false, true, false, true},

        {MigrationExecution.Serial(MigrationSerialOrder.RANDOM), MigrationStage.OFF, true, false, true, false},
        {MigrationExecution.Serial(MigrationSerialOrder.RANDOM), MigrationStage.DUAL_WRITE, true, false, true, true},
        {MigrationExecution.Serial(MigrationSerialOrder.RANDOM), MigrationStage.SHADOW, true, true, true, true},
        {MigrationExecution.Serial(MigrationSerialOrder.RANDOM), MigrationStage.LIVE, true, true, true, true},
        {MigrationExecution.Serial(MigrationSerialOrder.RANDOM), MigrationStage.RAMP_DOWN, false, true, true, true},
        {MigrationExecution.Serial(MigrationSerialOrder.RANDOM), MigrationStage.COMPLETE, false, true, false, true}
    });
  }

  /**
   * The parameterization is done using a constructor here so that the base class can be parameterized.
   * This did not combine well with an outer class using Enclosed.class.
   *
   * @param execution to test
   * @param stage to test
   * @param expectReadOldCalled expected read old
   * @param expectReadNewCalled expected read new
   * @param expectWriteOldCalled expected write old
   * @param expectWriteNewCalled expected write new
   */
  public MigrationStagesExpectedExecutionTests(
      MigrationExecution execution,
      MigrationStage stage,
      boolean expectReadOldCalled,
      boolean expectReadNewCalled,
      boolean expectWriteOldCalled,
      boolean expectWriteNewCalled
  ) {
    super(execution);
    this.stage = stage;
    this.expectReadOldCalled = expectReadOldCalled;
    this.expectReadNewCalled = expectReadNewCalled;
    this.expectWriteOldCalled = expectWriteOldCalled;
    this.expectWriteNewCalled = expectWriteNewCalled;
  }

  public MigrationStage stage;
  public boolean expectReadOldCalled;
  public boolean expectReadNewCalled;
  public boolean expectWriteOldCalled;
  public boolean expectWriteNewCalled;

  private void assertReads() {
    assertEquals("Expected read old", expectReadOldCalled, readOldCalled);
    assertEquals("Expected read new", expectReadNewCalled, readNewCalled);
    // For a read there should be no writes.
    assertFalse("Expected write old", writeOldCalled);
    assertFalse("Expected write new", writeNewCalled);
  }

  private void assertWrites() {
    assertEquals("Expected write old", expectWriteOldCalled, writeOldCalled);
    assertEquals("Expected write new", expectWriteNewCalled, writeNewCalled);
    // For a write there should be no reads.
    assertFalse("Expected read old", readOldCalled);
    assertFalse("Expected read new", readNewCalled);
  }


  @Test
  public void itReadsFromCorrectSources() {
    // No flag config here, just evaluate using the defaults.
    Migration.MigrationResult<String> res = migration.read(flagKey, LDContext.create("user-key"), stage);
    assertTrue(res.isSuccess());
    assertTrue(res.getResult().isPresent());
    switch(stage) {
      case OFF: // Fallthrough cases that have authoritative old.
      case DUAL_WRITE:
      case SHADOW:
        assertEquals("Old", res.getResult().get());
        break;
      case LIVE: // Fallthrough cases that have authoritative new.
      case RAMP_DOWN:
      case COMPLETE:
        assertEquals("New", res.getResult().get());
        break;
    }
    assertReads();
  }

  @Test
  public void itWritesToCorrectSources() {
    // No flag config here, just evaluate using the defaults.
    Migration.MigrationWriteResult<String> res = migration.write(flagKey, LDContext.create("user-key"), stage);

    assertTrue(res.getAuthoritative().isSuccess());
    assertTrue(res.getAuthoritative().getResult().isPresent());
    switch(stage) {
      case OFF:
        assertEquals("Old", res.getAuthoritative().getResult().get());
        break;
      case DUAL_WRITE: // Dual write and shadow do the same thing.
      case SHADOW:
        assertTrue(res.getNonAuthoritative().isPresent());
        assertTrue(res.getNonAuthoritative().get().isSuccess());
        assertEquals("New", res.getNonAuthoritative().get().getResult().get());
        assertEquals("Old", res.getAuthoritative().getResult().get());
        break;
      case LIVE: // Live and rampdown do the same thing.
      case RAMP_DOWN:
        assertTrue(res.getNonAuthoritative().isPresent());
        assertTrue(res.getNonAuthoritative().get().isSuccess());
        assertEquals("Old", res.getNonAuthoritative().get().getResult().get());
        assertEquals("New", res.getAuthoritative().getResult().get());
        break;
      case COMPLETE:
        assertEquals("New", res.getAuthoritative().getResult().get());
        break;
    }

    assertWrites();
  }

  @Test
  public void itReportsReadOperationsCorrectly() {
    migration.read(flagKey, LDContext.create("user-key"), stage);
    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp)e;
    assertEquals(MigrationOp.READ.toString(), me.getOperation());
  }

  @Test
  public void itReportsWriteOperationsCorrectly() {
    migration.write(flagKey, LDContext.create("user-key"), stage);
    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp)e;
    assertEquals(MigrationOp.WRITE.toString(), me.getOperation());
  }

  @Test
  public void itReportsTheCorrectOriginForReadOperations() {
    Migration.MigrationResult<String> res = migration.read(flagKey, LDContext.create("user-key"), stage);
    switch(stage) {
      case OFF: // Fallthrough cases that have authoritative old.
      case DUAL_WRITE:
      case SHADOW:
        assertEquals(MigrationOrigin.OLD, res.getOrigin());
        break;
      case LIVE: // Fallthrough cases that have authoritative new.
      case RAMP_DOWN:
      case COMPLETE:
        assertEquals(MigrationOrigin.NEW, res.getOrigin());
        break;
    }
  }

  @Test
  public void itReportsTheCorrectOriginForWriteOperations() {
    Migration.MigrationWriteResult<String> res = migration.write(flagKey, LDContext.create("user-key"), stage);

    switch(stage) {
      case OFF:
        assertEquals(MigrationOrigin.OLD, res.getAuthoritative().getOrigin());
        break;
      case DUAL_WRITE: // Dual write and shadow do the same thing.
      case SHADOW:
        assertEquals(MigrationOrigin.OLD, res.getAuthoritative().getOrigin());
        assertEquals(MigrationOrigin.NEW, res.getNonAuthoritative().get().getOrigin());
        break;
      case LIVE: // Live and rampdown do the same thing.
      case RAMP_DOWN:
        assertEquals(MigrationOrigin.NEW, res.getAuthoritative().getOrigin());
        assertEquals(MigrationOrigin.OLD, res.getNonAuthoritative().get().getOrigin());
        break;
      case COMPLETE:
        assertEquals(MigrationOrigin.NEW, res.getAuthoritative().getOrigin());
        break;
    }
  }

  @Test public void itReportsReadErrorsCorrectlyForOld() {
    failOldRead = true;
    Migration.MigrationResult<String> res = migration.read(flagKey, LDContext.create("user-key"), stage);
    switch(stage) {
      case OFF: // Fallthrough cases that have authoritative old.
      case DUAL_WRITE:
      case SHADOW:
        assertFalse(res.isSuccess());
        break;
      case LIVE: // Fallthrough cases that have authoritative new.
      case RAMP_DOWN:
      case COMPLETE:
        assertTrue(res.isSuccess());
        break;
    }

    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp)e;

    if(expectReadOldCalled) {
      assertNotNull(me.getErrorMeasurement());
      assertTrue(me.getErrorMeasurement().hasOldError());
    } else {
      assertNull(me.getErrorMeasurement());
    }
  }

  @Test public void itReportsReadErrorsCorrectlyForNew() {
    failNewRead = true;
    Migration.MigrationResult<String> res = migration.read(flagKey, LDContext.create("user-key"), stage);
    switch(stage) {
      case OFF: // Fallthrough cases that have authoritative old.
      case DUAL_WRITE:
      case SHADOW:
        assertTrue(res.isSuccess());
        break;
      case LIVE: // Fallthrough cases that have authoritative new.
      case RAMP_DOWN:
      case COMPLETE:
        assertFalse(res.isSuccess());
        break;
    }
    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp)e;

    if(expectReadNewCalled) {
      assertNotNull(me.getErrorMeasurement());
      assertTrue(me.getErrorMeasurement().hasNewError());
    } else {
      assertNull(me.getErrorMeasurement());
    }
  }

  @Test public void itReportsReadErrorsCorrectlyForBoth() {
    failNewRead = true;
    failOldRead = true;
    migration.read(flagKey, LDContext.create("user-key"), stage);
    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp)e;
    assertNotNull(me.getErrorMeasurement());

    if(expectReadNewCalled) {
      assertTrue(me.getErrorMeasurement().hasNewError());
    } else {
      assertFalse(me.getErrorMeasurement().hasNewError());
    }

    if(expectReadOldCalled) {
      assertTrue(me.getErrorMeasurement().hasOldError());
    } else {
      assertFalse(me.getErrorMeasurement().hasOldError());
    }
  }

  @Test
  public void itAddsTheCorrectInvokedMeasurementsForReads() {
    migration.read(flagKey, LDContext.create("user-key"), stage);
    assertEquals(2, eventSink.events.size());
    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp)e;

    assertNotNull(me.getInvokedMeasurement());
    if(expectReadOldCalled) {
      assertTrue(me.getInvokedMeasurement().wasOldInvoked());
    } else {
      assertFalse(me.getInvokedMeasurement().wasOldInvoked());
    }
    if(expectReadNewCalled) {
      assertTrue(me.getInvokedMeasurement().wasNewInvoked());
    } else {
      assertFalse(me.getInvokedMeasurement().wasNewInvoked());
    }
  }

  @Test
  public void itAddsTheCorrectInvokedMeasurementsForWrites() {
    migration.write(flagKey, LDContext.create("user-key"), stage);
    assertEquals(2, eventSink.events.size());
    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp)e;

    assertNotNull(me.getInvokedMeasurement());
    if(expectWriteOldCalled) {
      assertTrue(me.getInvokedMeasurement().wasOldInvoked());
    } else {
      assertFalse(me.getInvokedMeasurement().wasOldInvoked());
    }
    if(expectWriteNewCalled) {
      assertTrue(me.getInvokedMeasurement().wasNewInvoked());
    } else {
      assertFalse(me.getInvokedMeasurement().wasNewInvoked());
    }
  }

  @Test
  public void itDoesNotReportErrorsWhenThereAreNoneForReads() {
    migration.read(flagKey, LDContext.create("user-key"), stage);
    assertEquals(2, eventSink.events.size());
    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp)e;
    assertNull(me.getErrorMeasurement());
  }

  @Test
  public void itDoesNotReportErrorsWhenThereAreNoneForWrites() {
    migration.write(flagKey, LDContext.create("user-key"), stage);
    assertEquals(2, eventSink.events.size());
    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp)e;
    assertNull(me.getErrorMeasurement());
  }

  @Test
  public void itDoesReportLatencyForReads() {
    migration.read(flagKey, LDContext.create("user-key"), stage);
    assertEquals(2, eventSink.events.size());
    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp)e;

    assertNotNull(me.getLatencyMeasurement());
    if(expectReadOldCalled) {
      assertNotNull(me.getLatencyMeasurement().getOldLatencyMs());
    } else {
      assertNull(me.getLatencyMeasurement().getOldLatencyMs());
    }
    if(expectReadNewCalled) {
      assertNotNull(me.getLatencyMeasurement().getNewLatencyMs());
    } else {
      assertNull(me.getLatencyMeasurement().getNewLatencyMs());
    }
  }

  @Test
  public void itDoesReportLatencyForWrites() {
    migration.write(flagKey, LDContext.create("user-key"), stage);
    assertEquals(2, eventSink.events.size());
    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp)e;

    assertNotNull(me.getLatencyMeasurement());
    if(expectWriteOldCalled) {
      assertNotNull(me.getLatencyMeasurement().getOldLatencyMs());
    } else {
      assertNull(me.getLatencyMeasurement().getOldLatencyMs());
    }
    if(expectWriteNewCalled) {
      assertNotNull(me.getLatencyMeasurement().getNewLatencyMs());
    } else {
      assertNull(me.getLatencyMeasurement().getNewLatencyMs());
    }
  }
}
