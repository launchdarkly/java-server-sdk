package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.integrations.TestData;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import com.launchdarkly.sdk.server.migrations.Migration;
import com.launchdarkly.sdk.server.migrations.MigrationBuilder;
import com.launchdarkly.sdk.server.migrations.MigrationExecution;
import com.launchdarkly.sdk.server.migrations.MigrationMethodResult;

import static com.launchdarkly.sdk.server.TestComponents.specificComponent;

import java.util.Optional;

import static org.junit.Assert.assertTrue;

/**
 * This fixtures simplifies tests which require tracking the execution of the various migration methods.
 */
public class MigrationExecutionFixture extends BaseTest {
  public final TestData testData = TestData.dataSource();
  public final String flagKey = "test-flag";

  public TestComponents.TestEventProcessor eventSink = new TestComponents.TestEventProcessor();

  public final LDClientInterface client = new LDClient("SDK_KEY", baseConfig()
      .dataSource(testData)
      .events(specificComponent(eventSink))
      .build());

  public Migration<String, String, String, String> migration;

  public boolean readOldCalled = false;
  public boolean writeOldCalled = false;
  public boolean readNewCalled = false;
  public boolean writeNewCalled = false;

  public boolean failOldWrite = false;

  public boolean failNewWrite = false;

  public boolean failOldRead = false;

  public boolean failNewRead = false;

  public String payloadReadOld = null;
  public String payloadReadNew = null;

  public String payloadWriteOld = null;
  public String payloadWriteNew = null;

  public MigrationExecutionFixture(MigrationExecution execution) {
    this(execution, true, true);
  }

  public MigrationExecutionFixture(MigrationExecution execution, boolean trackLatency, boolean trackErrors) {
    MigrationBuilder<String, String, String, String> builder = new MigrationBuilder<String, String, String, String>(client).
        readExecution(execution)
        .trackLatency(trackLatency)
        .trackErrors(trackErrors)
        .read((payload) -> {
          readOldCalled = true;
          payloadReadOld = payload;
          return failOldRead ? MigrationMethodResult.Failure() : MigrationMethodResult.Success("Old");
        }, (payload) -> {
          readNewCalled = true;
          payloadReadNew = payload;
          return failNewRead ? MigrationMethodResult.Failure() : MigrationMethodResult.Success("New");
        })
        .write((payload) -> {
          writeOldCalled = true;
          payloadWriteOld = payload;
          return failOldWrite ? MigrationMethodResult.Failure() : MigrationMethodResult.Success("Old");
        }, (payload) -> {
          writeNewCalled = true;
          payloadWriteNew = payload;
          return failNewWrite ? MigrationMethodResult.Failure() : MigrationMethodResult.Success("New");
        });
    Optional<Migration<String, String, String, String>> res = builder.build();
    assertTrue(res.isPresent());
    migration = res.get();
  }
}
