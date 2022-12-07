package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.CacheStats;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.Status;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.assertNoMoreValues;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.awaitValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@SuppressWarnings("javadoc")
public class DataStoreStatusProviderImplTest extends BaseTest {
  private EventBroadcasterImpl<DataStoreStatusProvider.StatusListener, DataStoreStatusProvider.Status> broadcaster =
      EventBroadcasterImpl.forDataStoreStatus(sharedExecutor, testLogger);
  private MockDataStore store = new MockDataStore();
  private DataStoreUpdatesImpl updates = new DataStoreUpdatesImpl(broadcaster);
  private DataStoreStatusProviderImpl statusProvider = new DataStoreStatusProviderImpl(store, updates);
  
  @Test
  public void getStatus() throws Exception {
    assertThat(statusProvider.getStatus(), equalTo(new Status(true, false)));
    
    updates.updateStatus(new Status(false, false));
    
    assertThat(statusProvider.getStatus(), equalTo(new Status(false, false)));
    
    updates.updateStatus(new Status(false, true));
    
    assertThat(statusProvider.getStatus(), equalTo(new Status(false, true)));
  }
  
  @Test
  public void statusListeners() throws Exception {
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    statusProvider.addStatusListener(statuses::add);

    BlockingQueue<Status> unwantedStatuses = new LinkedBlockingQueue<>();
    DataStoreStatusProvider.StatusListener listener2 = unwantedStatuses::add;
    statusProvider.addStatusListener(listener2);
    statusProvider.removeStatusListener(listener2); // testing that a listener can be unregistered
    
    updates.updateStatus(new Status(false, false));

    Status newStatus = awaitValue(statuses, 500, TimeUnit.MILLISECONDS);
    assertThat(newStatus, equalTo(new Status(false, false)));
    
    assertNoMoreValues(unwantedStatuses, 100, TimeUnit.MILLISECONDS);
  }
  
  @Test
  public void isStatusMonitoringEnabled() {
    assertThat(statusProvider.isStatusMonitoringEnabled(), equalTo(false));
    
    store.statusMonitoringEnabled = true;
    
    assertThat(statusProvider.isStatusMonitoringEnabled(), equalTo(true));
  }
  
  @Test
  public void cacheStats() {
    assertThat(statusProvider.getCacheStats(), nullValue());
    
    CacheStats stats = new CacheStats(0, 0, 0, 0, 0, 0);
    store.cacheStats = stats;
    
    assertThat(statusProvider.getCacheStats(), equalTo(stats));
  }
  
  private static final class MockDataStore implements DataStore {
    volatile boolean statusMonitoringEnabled;
    volatile CacheStats cacheStats;
    
    @Override
    public void close() throws IOException {}

    @Override
    public void init(FullDataSet<ItemDescriptor> allData) {}

    @Override
    public ItemDescriptor get(DataKind kind, String key) {
      return null;
    }

    @Override
    public KeyedItems<ItemDescriptor> getAll(DataKind kind) {
      return null;
    }

    @Override
    public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
      return false;
    }

    @Override
    public boolean isInitialized() {
      return false;
    }

    @Override
    public boolean isStatusMonitoringEnabled() {
      return statusMonitoringEnabled;
    }

    @Override
    public CacheStats getCacheStats() {
      return cacheStats;
    }
  }
}
