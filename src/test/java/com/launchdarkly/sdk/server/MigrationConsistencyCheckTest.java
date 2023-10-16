package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.internal.events.Event;
import com.launchdarkly.sdk.server.integrations.TestData;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import com.launchdarkly.sdk.server.migrations.Migration;
import com.launchdarkly.sdk.server.migrations.MigrationBuilder;
import com.launchdarkly.sdk.server.migrations.MigrationExecution;
import com.launchdarkly.sdk.server.migrations.MigrationMethodResult;
import com.launchdarkly.sdk.server.migrations.MigrationSerialOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.launchdarkly.sdk.server.TestComponents.specificComponent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class MigrationConsistencyCheckTest extends BaseTest {
  public TestComponents.TestEventProcessor eventSink = new TestComponents.TestEventProcessor();
  public final TestData testData = TestData.dataSource();

  public final LDClientInterface client = new LDClient("SDK_KEY", baseConfig()
    .dataSource(testData)
    .events(specificComponent(eventSink))
    .build());

  public Migration<String, String, String, String> migration;

  public String readOldResult = "";
  public String readNewResult = "";

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {MigrationExecution.Parallel()},
      {MigrationExecution.Serial(MigrationSerialOrder.FIXED)},
      {MigrationExecution.Serial(MigrationSerialOrder.RANDOM)}
    });
  }

  public MigrationConsistencyCheckTest(MigrationExecution execution) {
    MigrationBuilder<String, String, String, String> builder = new MigrationBuilder<String, String, String, String>(client)
      .readExecution(execution)
      .read(
        (payload) -> MigrationMethodResult.Success(readOldResult),
        (payload) -> MigrationMethodResult.Success(readNewResult),
        (a, b) -> Objects.equals(a, b))
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
  public void itFindsResultsConsistentWhenTheyAre() {
    readOldResult = "consistent";
    readNewResult = "consistent";

    migration.read("test-flag",
      LDContext.create("user-key"), MigrationStage.LIVE);

    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp) e;
    assertNotNull(me.getConsistencyMeasurement());
    assertTrue(me.getConsistencyMeasurement().isConsistent());
  }

  @Test
  public void itFindsResultsInconsistentWhenTheyAre() {
    readOldResult = "consistent";
    readNewResult = "inconsistent";

    migration.read("test-flag",
      LDContext.create("user-key"), MigrationStage.LIVE);

    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp) e;
    assertNotNull(me.getConsistencyMeasurement());
    assertFalse(me.getConsistencyMeasurement().isConsistent());
  }

  @Test
  public void itDoesNotRunTheCheckIfCheckRatioIsZero() {
    readOldResult = "consistent";
    readNewResult = "inconsistent";

    testData.update(testData.flag("test-flag")
      .on(true)
      .valueForAll(LDValue.of("shadow"))
      .migrationCheckRatio(0));

    migration.read("test-flag",
      LDContext.create("user-key"), MigrationStage.LIVE);

    Event e = eventSink.events.get(1);
    assertEquals(Event.MigrationOp.class, e.getClass());
    Event.MigrationOp me = (Event.MigrationOp) e;
    assertNull(me.getConsistencyMeasurement());
  }
}
