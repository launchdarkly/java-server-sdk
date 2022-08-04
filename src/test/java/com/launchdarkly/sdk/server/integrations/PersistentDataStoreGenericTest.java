package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.sdk.server.TestComponents;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.PersistentDataStore;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This verifies that PersistentDataStoreTestBase behaves as expected as long as the PersistentDataStore
 * implementation behaves as expected. Since there aren't any actual database integrations built into the
 * SDK project, and PersistentDataStoreTestBase will be used by external projects like java-server-sdk-redis,
 * we want to make sure the test logic is correct regardless of database implementation details.
 * 
 * PersistentDataStore implementations may be able to persist the version and deleted state as metadata
 * separate from the serialized item string; or they may not, in which case a little extra parsing is
 * necessary. MockPersistentDataStore is able to simulate both of these scenarios, and we test both here. 
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class PersistentDataStoreGenericTest extends PersistentDataStoreTestBase<MockPersistentDataStore> {
  private final MockPersistentDataStore.MockDatabaseInstance sharedData = new MockPersistentDataStore.MockDatabaseInstance();
  private final TestMode testMode;

  static class TestMode {
    final boolean persistOnlyAsString;
    
    TestMode(boolean persistOnlyAsString) {
      this.persistOnlyAsString = persistOnlyAsString;
    }
    
    @Override
    public String toString() {
      return "TestMode(" + (persistOnlyAsString ? "persistOnlyAsString" : "persistWithMetadata") + ")";
    }
  }

  @Parameters(name="{0}")
  public static Iterable<TestMode> data() {
    return ImmutableList.of(
        new TestMode(false),
        new TestMode(true)
        );
  }

  public PersistentDataStoreGenericTest(TestMode testMode) {
    this.testMode = testMode;
  }

  @Override
  protected ComponentConfigurer<PersistentDataStore> buildStore(String prefix) {
    MockPersistentDataStore store = new MockPersistentDataStore(sharedData, prefix);
    store.persistOnlyAsString = testMode.persistOnlyAsString;
    return TestComponents.specificComponent(store);
  }

  @Override
  protected void clearAllData() {
    synchronized (sharedData) {
      for (String prefix: sharedData.dataByPrefix.keySet()) {
        sharedData.dataByPrefix.get(prefix).clear();
      }
    }
  }
  
  @Override
  protected boolean setUpdateHook(MockPersistentDataStore storeUnderTest, Runnable hook) {
    storeUnderTest.updateHook = hook;
    return true;
  }
}
