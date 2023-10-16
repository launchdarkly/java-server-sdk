package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.integrations.TestData;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import com.launchdarkly.sdk.server.migrations.Migration;
import com.launchdarkly.sdk.server.migrations.MigrationBuilder;
import com.launchdarkly.sdk.server.migrations.MigrationMethodResult;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MigrationBuilderTests extends BaseTest {
  private final TestData testData = TestData.dataSource();

  private final LDClientInterface client = new LDClient("SDK_KEY", baseConfig()
      .dataSource(testData)
      .build());

  @Test
  public void itCanMakeABasicMigration() {
    MigrationBuilder<String, String, Void, Void> builder = new MigrationBuilder<>(client);
    builder.read((Void) -> MigrationMethodResult.Success("Old"), (Void)-> MigrationMethodResult.Success("New"));
    builder.write((Void) -> MigrationMethodResult.Success("Old"), (Void)-> MigrationMethodResult.Success("New"));
    Optional<Migration<String, String, Void, Void>> migration = builder.build();
    assertTrue(migration.isPresent());
  }

  @Test
  public void itDoesNotCreateAMigrationIfReadImplementationIsNotSet() {
    MigrationBuilder<String, String, Void, Void> builder = new MigrationBuilder<>(client);
    builder.write((Void) -> MigrationMethodResult.Success("Old"), (Void)-> MigrationMethodResult.Success("New"));
    Optional<Migration<String, String, Void, Void>> migration = builder.build();
    assertFalse(migration.isPresent());
  }

  @Test
  public void itDoesNotCreateAMigrationIfWriteImplementationIsNotSet() {
    MigrationBuilder<String, String, Void, Void> builder = new MigrationBuilder<>(client);
    builder.read((Void) -> MigrationMethodResult.Success("Old"), (Void)-> MigrationMethodResult.Success("New"));
    Optional<Migration<String, String, Void, Void>> migration = builder.build();
    assertFalse(migration.isPresent());
  }
}
