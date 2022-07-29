package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.Status;

import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.assertNoMoreValues;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.awaitValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class DataStoreUpdatesImplTest extends BaseTest {
  private EventBroadcasterImpl<DataStoreStatusProvider.StatusListener, DataStoreStatusProvider.Status> broadcaster =
      EventBroadcasterImpl.forDataStoreStatus(sharedExecutor, testLogger);
  private final DataStoreUpdatesImpl updates = new DataStoreUpdatesImpl(broadcaster);
  
  @Test
  public void updateStatusBroadcastsNewStatus() {
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    broadcaster.register(statuses::add);

    updates.updateStatus(new Status(false, false));
    
    Status newStatus = awaitValue(statuses, 200, TimeUnit.MILLISECONDS);
    assertThat(newStatus, equalTo(new Status(false, false)));
    
    assertNoMoreValues(statuses, 100, TimeUnit.MILLISECONDS);
  }

  @Test
  public void updateStatusDoesNothingIfNewStatusIsSame() {
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    broadcaster.register(statuses::add);

    updates.updateStatus(new Status(true, false));
    
    assertNoMoreValues(statuses, 100, TimeUnit.MILLISECONDS);
  }

  @Test
  public void updateStatusDoesNothingIfNewStatusIsNull() {
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    broadcaster.register(statuses::add);

    updates.updateStatus(null);
    
    assertNoMoreValues(statuses, 100, TimeUnit.MILLISECONDS);
  }
}
