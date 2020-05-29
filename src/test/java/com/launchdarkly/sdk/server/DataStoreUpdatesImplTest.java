package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.Status;

import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static com.launchdarkly.sdk.server.TestUtil.expectNoMoreValues;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class DataStoreUpdatesImplTest {
  private EventBroadcasterImpl<DataStoreStatusProvider.StatusListener, DataStoreStatusProvider.Status> broadcaster =
      EventBroadcasterImpl.forDataStoreStatus(sharedExecutor);
  private final DataStoreUpdatesImpl updates = new DataStoreUpdatesImpl(broadcaster);
  
  @Test
  public void updateStatusBroadcastsNewStatus() {
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    broadcaster.register(statuses::add);

    updates.updateStatus(new Status(false, false));
    
    Status newStatus = TestUtil.awaitValue(statuses, Duration.ofMillis(200));
    assertThat(newStatus, equalTo(new Status(false, false)));
    
    expectNoMoreValues(statuses, Duration.ofMillis(100));
  }

  @Test
  public void updateStatusDoesNothingIfNewStatusIsSame() {
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    broadcaster.register(statuses::add);

    updates.updateStatus(new Status(true, false));
    
    expectNoMoreValues(statuses, Duration.ofMillis(100));
  }

  @Test
  public void updateStatusDoesNothingIfNewStatusIsNull() {
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    broadcaster.register(statuses::add);

    updates.updateStatus(null);
    
    expectNoMoreValues(statuses, Duration.ofMillis(100));
  }
}
