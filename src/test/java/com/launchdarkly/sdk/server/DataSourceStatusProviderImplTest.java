package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.Status;
import com.launchdarkly.testhelpers.ConcurrentHelpers;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.assertNoMoreValues;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.trySleep;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.sameInstance;

@SuppressWarnings("javadoc")
public class DataSourceStatusProviderImplTest extends BaseTest {
  private EventBroadcasterImpl<DataSourceStatusProvider.StatusListener, DataSourceStatusProvider.Status> broadcaster =
      EventBroadcasterImpl.forDataSourceStatus(sharedExecutor, testLogger);
  private DataSourceUpdatesImpl updates = new DataSourceUpdatesImpl(
      TestComponents.inMemoryDataStore(),
      null,
      null,
      broadcaster,
      sharedExecutor,
      null,
      testLogger
      );
  private DataSourceStatusProviderImpl statusProvider = new DataSourceStatusProviderImpl(broadcaster, updates);
  
  @Test
  public void getStatus() throws Exception {
    assertThat(statusProvider.getStatus().getState(), equalTo(State.INITIALIZING));
    
    Instant timeBefore = Instant.now();
    ErrorInfo errorInfo = ErrorInfo.fromHttpError(500);
    
    updates.updateStatus(State.VALID, errorInfo);
    
    Status newStatus = statusProvider.getStatus();
    assertThat(newStatus.getState(), equalTo(State.VALID));
    assertThat(newStatus.getStateSince(), greaterThanOrEqualTo(timeBefore));
    assertThat(newStatus.getLastError(), sameInstance(errorInfo));
  }
  
  @Test
  public void statusListeners() throws Exception {
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    statusProvider.addStatusListener(statuses::add);

    BlockingQueue<Status> unwantedStatuses = new LinkedBlockingQueue<>();
    DataSourceStatusProvider.StatusListener listener2 = unwantedStatuses::add;
    statusProvider.addStatusListener(listener2);
    statusProvider.removeStatusListener(listener2); // testing that a listener can be unregistered
    
    updates.updateStatus(State.VALID, null);

    Status newStatus = ConcurrentHelpers.awaitValue(statuses, 500, TimeUnit.MILLISECONDS);
    assertThat(newStatus.getState(), equalTo(State.VALID));
    
    assertNoMoreValues(unwantedStatuses, 100, TimeUnit.MILLISECONDS);
  }
  
  @Test
  public void waitForStatusWithStatusAlreadyCorrect() throws Exception {
    updates.updateStatus(State.VALID, null);
    
    boolean success = statusProvider.waitFor(State.VALID, Duration.ofMillis(500));
    assertThat(success, equalTo(true));
  }

  @Test
  public void waitForStatusSucceeds() throws Exception {
    new Thread(() -> {
      trySleep(100, TimeUnit.MILLISECONDS);
      updates.updateStatus(State.VALID, null);
    }).start();

    boolean success = statusProvider.waitFor(State.VALID, Duration.ZERO);
    assertThat(success, equalTo(true));
  }

  @Test
  public void waitForStatusTimesOut() throws Exception {
    long timeStart = System.currentTimeMillis();
    boolean success = statusProvider.waitFor(State.VALID, Duration.ofMillis(300));
    long timeEnd = System.currentTimeMillis();
    assertThat(success, equalTo(false));
    assertThat(timeEnd - timeStart, greaterThanOrEqualTo(270L));
  }
  
  @Test
  public void waitForStatusEndsIfShutDown() throws Exception {
    new Thread(() -> {
      updates.updateStatus(State.OFF, null);
    }).start();
      
    long timeStart = System.currentTimeMillis();
    boolean success = statusProvider.waitFor(State.VALID, Duration.ofMillis(500));
    long timeEnd = System.currentTimeMillis();
    assertThat(success, equalTo(false));
    assertThat(timeEnd - timeStart, lessThan(500L));
  }
}
