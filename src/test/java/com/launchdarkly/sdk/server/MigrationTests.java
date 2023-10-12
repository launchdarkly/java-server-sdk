package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.internal.events.Event;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import com.launchdarkly.sdk.server.migrations.Migration;
import com.launchdarkly.sdk.server.migrations.MigrationBuilder;
import com.launchdarkly.sdk.server.migrations.MigrationExecution;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class MigrationTests {

  @RunWith(Parameterized.class)
  public static class GivenStagesThatPerformOldWritesFirstTest extends MigrationExecutionFixture {
    public GivenStagesThatPerformOldWritesFirstTest() {
      super(MigrationExecution.Parallel());
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<?> data() {
      return Arrays.asList(
        MigrationStage.DUAL_WRITE,
        MigrationStage.SHADOW
      );
    }

    @Parameterized.Parameter
    public MigrationStage stage;

    @Test
    public void isStopsWritingWhenItEncountersAnError() {
      failOldWrite = true;
      Migration.MigrationWriteResult<String> res = migration.write(flagKey, LDContext.create("user-key"), stage);
      assertFalse(res.getAuthoritative().isSuccess());
      assertFalse(res.getNonAuthoritative().isPresent());
      // The old write should fail, so the new write will not be done.
      assertTrue(writeOldCalled);
      assertFalse(writeNewCalled);

      Event e = eventSink.events.get(1);
      assertEquals(Event.MigrationOp.class, e.getClass());
      Event.MigrationOp me = (Event.MigrationOp) e;

      assertFalse(me.getInvokedMeasurement().wasNewInvoked());
      assertTrue(me.getInvokedMeasurement().wasOldInvoked());

      assertNotNull(me.getErrorMeasurement());
      assertFalse(me.getErrorMeasurement().hasNewError());
      assertTrue(me.getErrorMeasurement().hasOldError());
    }
  }

  @RunWith(Parameterized.class)
  public static class GivenStagesThatPerformNewWritesFirstTest extends MigrationExecutionFixture {
    public GivenStagesThatPerformNewWritesFirstTest() {
      super(MigrationExecution.Parallel());
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<?> data() {
      return Arrays.asList(
        MigrationStage.LIVE,
        MigrationStage.RAMP_DOWN
      );
    }

    @Parameterized.Parameter
    public MigrationStage stage;

    @Test
    public void itStopsWritingWhenItEncountersAnError() {
      failNewWrite = true;
      Migration.MigrationWriteResult<String> res = migration.write(flagKey, LDContext.create("user-key"), stage);
      assertFalse(res.getAuthoritative().isSuccess());
      assertFalse(res.getNonAuthoritative().isPresent());
      // The new write should fail, so the new write will not be done.
      assertTrue(writeNewCalled);
      assertFalse(writeOldCalled);
      Event e = eventSink.events.get(1);
      assertEquals(Event.MigrationOp.class, e.getClass());
      Event.MigrationOp me = (Event.MigrationOp) e;

      assertTrue(me.getInvokedMeasurement().wasNewInvoked());
      assertFalse(me.getInvokedMeasurement().wasOldInvoked());

      assertNotNull(me.getErrorMeasurement());
      assertTrue(me.getErrorMeasurement().hasNewError());
      assertFalse(me.getErrorMeasurement().hasOldError());
    }
  }

  @RunWith(Parameterized.class)
  public static class GivenMigrationStagesExecutedWithPayloadsTest extends MigrationExecutionFixture {
    public GivenMigrationStagesExecutedWithPayloadsTest() {
      super(MigrationExecution.Parallel());
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][]{
        // Stage, read old payload, read new payload, write old payload, write new payload.
        {MigrationStage.OFF, "payload", null, "payload", null},
        {MigrationStage.DUAL_WRITE, "payload", null, "payload", "payload"},
        {MigrationStage.SHADOW, "payload", "payload", "payload", "payload"},
        {MigrationStage.LIVE, "payload", "payload", "payload", "payload"},
        {MigrationStage.RAMP_DOWN, null, "payload", "payload", "payload"},
        {MigrationStage.COMPLETE, null, "payload", null, "payload"}
      });
    }

    @Parameterized.Parameter
    public MigrationStage stage;

    @Parameterized.Parameter(value = 1)
    public String expectedOldReadPayload;

    @Parameterized.Parameter(value = 2)
    public String expectedNewReadPayload;

    @Parameterized.Parameter(value = 3)
    public String expectedOldWritePayload;

    @Parameterized.Parameter(value = 4)
    public String expectedNewWritePayload;

    private void assertReads() {
      assertEquals("Expected read old", expectedOldReadPayload, payloadReadOld);
      assertEquals("Expected read new", expectedNewReadPayload, payloadReadNew);
      // For a read there should be no writes.
      assertNull("Expected write old", payloadWriteOld);
      assertNull("Expected write new", payloadWriteNew);
    }

    private void assertWrites() {
      assertEquals("Expected write old", expectedOldWritePayload, payloadWriteOld);
      assertEquals("Expected write new", expectedNewWritePayload, payloadWriteNew);
      // For a write there should be no reads.
      assertNull("Expected read old", payloadReadOld);
      assertNull("Expected read new", payloadReadNew);
    }


    @Test
    public void itCorrectlyForwardsTheReadPayload() {
      // No flag config here, just evaluate using the defaults.
      migration.read(flagKey, LDContext.create("user-key"), stage, "payload");
      assertReads();
    }

    @Test
    public void itCorrectlyForwardsTheWritePayload() {
      // No flag config here, just evaluate using the defaults.
      migration.write(flagKey, LDContext.create("user-key"), stage, "payload");
      assertWrites();
    }
  }

  @RunWith(Parameterized.class)
  public static class GivenMigrationThatDoesNotTrackLatencyTest extends MigrationExecutionFixture {
    public GivenMigrationThatDoesNotTrackLatencyTest() {
      super(MigrationExecution.Parallel(),
        false, false);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][]{
        // Stage, read old payload, read new payload, write old payload, write new payload.
        {MigrationStage.OFF},
        {MigrationStage.DUAL_WRITE},
        {MigrationStage.SHADOW},
        {MigrationStage.LIVE},
        {MigrationStage.RAMP_DOWN},
        {MigrationStage.COMPLETE}
      });
    }

    @Parameterized.Parameter
    public MigrationStage stage;

    @Test
    public void itDoesNotTrackLatencyForReads() {
      migration.read(flagKey, LDContext.create("user-key"), stage);
      Event e = eventSink.events.get(1);
      assertEquals(Event.MigrationOp.class, e.getClass());
      Event.MigrationOp me = (Event.MigrationOp) e;
      assertNull(me.getLatencyMeasurement());
    }

    @Test
    public void itDoesNotTrackLatencyForWrites() {
      migration.write(flagKey, LDContext.create("user-key"), stage);
      Event e = eventSink.events.get(1);
      assertEquals(Event.MigrationOp.class, e.getClass());
      Event.MigrationOp me = (Event.MigrationOp) e;
      assertNull(me.getLatencyMeasurement());
    }
  }

  @RunWith(Parameterized.class)
  public static class GivenMigrationThatDoesNotTrackErrorsTest extends MigrationExecutionFixture {
    public GivenMigrationThatDoesNotTrackErrorsTest() {
      super(MigrationExecution.Parallel(),
        false, false);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][]{
        // Stage, read old payload, read new payload, write old payload, write new payload.
        {MigrationStage.OFF},
        {MigrationStage.DUAL_WRITE},
        {MigrationStage.SHADOW},
        {MigrationStage.LIVE},
        {MigrationStage.RAMP_DOWN},
        {MigrationStage.COMPLETE}
      });
    }

    @Parameterized.Parameter
    public MigrationStage stage;

    @Test
    public void itDoesNotTrackErrorsForReads() {
      failNewRead = true;
      failOldRead = true;
      migration.read(flagKey, LDContext.create("user-key"), stage);
      Event e = eventSink.events.get(1);
      assertEquals(Event.MigrationOp.class, e.getClass());
      Event.MigrationOp me = (Event.MigrationOp) e;
      assertNull(me.getErrorMeasurement());
    }

    @Test
    public void itDoesNotTrackErrorsForWrites() {
      failOldWrite = true;
      failNewWrite = true;
      migration.write(flagKey, LDContext.create("user-key"), stage);
      Event e = eventSink.events.get(1);
      assertEquals(Event.MigrationOp.class, e.getClass());
      Event.MigrationOp me = (Event.MigrationOp) e;
      assertNull(me.getErrorMeasurement());
    }
  }

  public static class GivenOperationsWhichThrowTest extends BaseTest {
    public final LDClientInterface client = new LDClient("SDK_KEY", baseConfig()
      .build());

    public Migration<String, String, String, String> migration;

    public GivenOperationsWhichThrowTest() {
      MigrationBuilder<String, String, String, String> builder = new MigrationBuilder<String, String, String, String>(client)
        .read((payload) -> {
          throw new RuntimeException("old read");
        }, (payload) -> {
          throw new RuntimeException("new read");
        })
        .write((payload) -> {
          throw new RuntimeException("old write");
        }, (payload) -> {
          throw new RuntimeException("new write");
        });
      Optional<Migration<String, String, String, String>> res = builder.build();
      assertTrue(res.isPresent());
      migration = res.get();
    }

    @Test
    public void itHandlesExceptionInOldRead() {
      Migration.MigrationResult<String> res = migration.read("test-flag",
        LDContext.create("user-key"), MigrationStage.OFF);

      assertFalse(res.isSuccess());
      Optional<Exception> exception = res.getException();
      assertTrue(exception.isPresent());
      assertEquals("old read", exception.get().getMessage());
    }

    @Test
    public void itHandlesExceptionInNewRead() {
      Migration.MigrationResult<String> res = migration.read("test-flag",
        LDContext.create("user-key"), MigrationStage.LIVE);

      assertFalse(res.isSuccess());
      Optional<Exception> exception = res.getException();
      assertTrue(exception.isPresent());
      assertEquals("new read", exception.get().getMessage());
    }

    @Test
    public void itHandlesExceptionInOldWrite() {
      Migration.MigrationWriteResult<String> res = migration.write("test-flag",
        LDContext.create("user-key"), MigrationStage.OFF);

      assertFalse(res.getAuthoritative().isSuccess());
      Optional<Exception> exception = res.getAuthoritative().getException();
      assertTrue(exception.isPresent());
      assertEquals("old write", exception.get().getMessage());
    }

    @Test
    public void itHandlesExceptionInNewWrite() {
      Migration.MigrationWriteResult<String> res = migration.write("test-flag",
        LDContext.create("user-key"), MigrationStage.LIVE);

      assertFalse(res.getAuthoritative().isSuccess());
      Optional<Exception> exception = res.getAuthoritative().getException();
      assertTrue(exception.isPresent());
      assertEquals("new write", exception.get().getMessage());
    }
  }
}
