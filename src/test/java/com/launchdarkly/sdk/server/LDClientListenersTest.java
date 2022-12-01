package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.TestComponents.ContextCapturingFactory;
import com.launchdarkly.sdk.server.integrations.MockPersistentDataStore;
import com.launchdarkly.sdk.server.integrations.TestData;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStore;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.PersistentDataStore;

import org.easymock.EasyMockSupport;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.launchdarkly.sdk.server.TestComponents.specificComponent;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.assertNoMoreValues;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.awaitValue;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This file contains tests for all of the event broadcaster/listener functionality in the client, plus
 * related methods for looking at the same kinds of status values that can be broadcast to listeners.
 * It uses mock implementations of the data source and data store, so that it is only the status
 * monitoring mechanisms that are being tested, not the status behavior of specific real components.
 * <p>
 * Parts of this functionality are also covered by lower-level component tests like
 * DataSourceUpdatesImplTest. However, the tests here verify that the client is wiring the components
 * together correctly so that they work from an application's point of view.
 */
@SuppressWarnings("javadoc")
public class LDClientListenersTest extends BaseTest {
  private final static String SDK_KEY = "SDK_KEY";
  
  @Test
  public void clientSendsFlagChangeEvents() throws Exception {
    String flagKey = "flagkey";
    TestData testData = TestData.dataSource();
    testData.update(testData.flag(flagKey).on(true));
    LDConfig config = baseConfig()
        .dataSource(testData)
        .events(Components.noEvents())
        .build();
    
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      BlockingQueue<FlagChangeEvent> eventSink1 = new LinkedBlockingQueue<>();
      BlockingQueue<FlagChangeEvent> eventSink2 = new LinkedBlockingQueue<>();
      FlagChangeListener listener1 = eventSink1::add;
      FlagChangeListener listener2 = eventSink2::add; // need to capture the method reference in a variable so it's the same instance when we unregister it
      client.getFlagTracker().addFlagChangeListener(listener1);
      client.getFlagTracker().addFlagChangeListener(listener2);
      
      assertNoMoreValues(eventSink1, 100, TimeUnit.MILLISECONDS);
      assertNoMoreValues(eventSink2, 100, TimeUnit.MILLISECONDS);
      
      testData.update(testData.flag(flagKey).on(false));
      
      FlagChangeEvent event1 = awaitValue(eventSink1, 1, TimeUnit.SECONDS);
      FlagChangeEvent event2 = awaitValue(eventSink2, 1, TimeUnit.SECONDS);
      assertThat(event1.getKey(), equalTo(flagKey));
      assertThat(event2.getKey(), equalTo(flagKey));
      assertNoMoreValues(eventSink1, 100, TimeUnit.MILLISECONDS);
      assertNoMoreValues(eventSink2, 100, TimeUnit.MILLISECONDS);
      
      client.getFlagTracker().removeFlagChangeListener(listener1);
      
      testData.update(testData.flag(flagKey).on(true));
  
      FlagChangeEvent event3 = awaitValue(eventSink2, 1, TimeUnit.SECONDS);
      assertThat(event3.getKey(), equalTo(flagKey));
      assertNoMoreValues(eventSink1, 100, TimeUnit.MILLISECONDS);
      assertNoMoreValues(eventSink2, 100, TimeUnit.MILLISECONDS);
    }
  }

  @Test
  public void clientSendsFlagValueChangeEvents() throws Exception {
    String flagKey = "important-flag";
    LDContext user = LDContext.create("important-user");
    LDContext otherUser = LDContext.create("unimportant-user");

    TestData testData = TestData.dataSource();
    testData.update(testData.flag(flagKey).on(false));
    
    LDConfig config = baseConfig()
        .dataSource(testData)
        .events(Components.noEvents())
        .build();
    
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      BlockingQueue<FlagValueChangeEvent> eventSink1 = new LinkedBlockingQueue<>();
      BlockingQueue<FlagValueChangeEvent> eventSink2 = new LinkedBlockingQueue<>();
      BlockingQueue<FlagValueChangeEvent> eventSink3 = new LinkedBlockingQueue<>();
      client.getFlagTracker().addFlagValueChangeListener(flagKey, user, eventSink1::add);
      FlagChangeListener listener2 = client.getFlagTracker().addFlagValueChangeListener(flagKey, user, eventSink2::add);
      client.getFlagTracker().removeFlagChangeListener(listener2); // just verifying that the remove method works
      client.getFlagTracker().addFlagValueChangeListener(flagKey, otherUser, eventSink3::add);
      
      assertNoMoreValues(eventSink1, 100, TimeUnit.MILLISECONDS);
      assertNoMoreValues(eventSink2, 100, TimeUnit.MILLISECONDS);
      assertNoMoreValues(eventSink3, 100, TimeUnit.MILLISECONDS);
      
      // make the flag true for the first user only, and broadcast a flag change event
      testData.update(testData.flag(flagKey)
          .on(true)
          .variationForUser(user.getKey(), true)
          .fallthroughVariation(false));
      
      // eventSink1 receives a value change event
      FlagValueChangeEvent event1 = awaitValue(eventSink1, 1, TimeUnit.SECONDS);
      assertThat(event1.getKey(), equalTo(flagKey));
      assertThat(event1.getOldValue(), equalTo(LDValue.of(false)));
      assertThat(event1.getNewValue(), equalTo(LDValue.of(true)));
      assertNoMoreValues(eventSink1, 100, TimeUnit.MILLISECONDS);
      
      // eventSink2 doesn't receive one, because it was unregistered
      assertNoMoreValues(eventSink2, 100, TimeUnit.MILLISECONDS);
      
      // eventSink3 doesn't receive one, because the flag's value hasn't changed for otherUser
      assertNoMoreValues(eventSink2, 100, TimeUnit.MILLISECONDS);
    }
  }
  
  @Test
  public void dataSourceStatusProviderReturnsLatestStatus() throws Exception {
    TestData testData = TestData.dataSource();
    LDConfig config = baseConfig()
        .dataSource(testData)
        .events(Components.noEvents())
        .build();

    Instant timeBeforeStarting = Instant.now();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      DataSourceStatusProvider.Status initialStatus = client.getDataSourceStatusProvider().getStatus();
      assertThat(initialStatus.getState(), equalTo(DataSourceStatusProvider.State.VALID));
      assertThat(initialStatus.getStateSince(), greaterThanOrEqualTo(timeBeforeStarting));
      assertThat(initialStatus.getLastError(), nullValue());
      
      DataSourceStatusProvider.ErrorInfo errorInfo = new DataSourceStatusProvider.ErrorInfo(
          DataSourceStatusProvider.ErrorKind.ERROR_RESPONSE, 401, null, Instant.now());
      testData.updateStatus(DataSourceStatusProvider.State.OFF, errorInfo);

      DataSourceStatusProvider.Status newStatus = client.getDataSourceStatusProvider().getStatus();
      assertThat(newStatus.getState(), equalTo(DataSourceStatusProvider.State.OFF));
      assertThat(newStatus.getStateSince(), greaterThanOrEqualTo(errorInfo.getTime()));
      assertThat(newStatus.getLastError(), equalTo(errorInfo));
    }
  }

  @Test
  public void dataSourceStatusProviderSendsStatusUpdates() throws Exception {
    TestData testData = TestData.dataSource();
    LDConfig config = baseConfig()
        .dataSource(testData)
        .events(Components.noEvents())
        .build();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      BlockingQueue<DataSourceStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
      client.getDataSourceStatusProvider().addStatusListener(statuses::add);

      DataSourceStatusProvider.ErrorInfo errorInfo = new DataSourceStatusProvider.ErrorInfo(
          DataSourceStatusProvider.ErrorKind.ERROR_RESPONSE, 401, null, Instant.now());
      testData.updateStatus(DataSourceStatusProvider.State.OFF, errorInfo);

      DataSourceStatusProvider.Status newStatus = statuses.take();
      assertThat(newStatus.getState(), equalTo(DataSourceStatusProvider.State.OFF));
      assertThat(newStatus.getStateSince(), greaterThanOrEqualTo(errorInfo.getTime()));
      assertThat(newStatus.getLastError(), equalTo(errorInfo));
    }
  }
  
  @Test
  public void dataStoreStatusMonitoringIsDisabledForInMemoryStore() throws Exception {
    LDConfig config = baseConfig()
        .dataSource(Components.externalUpdatesOnly())
        .events(Components.noEvents())
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertThat(client.getDataStoreStatusProvider().isStatusMonitoringEnabled(), equalTo(false));
    }
  }

  @Test
  public void dataStoreStatusMonitoringIsEnabledForPersistentStore() throws Exception {
    LDConfig config = baseConfig()
        .dataSource(Components.externalUpdatesOnly())
        .dataStore(
            Components.persistentDataStore(TestComponents.<PersistentDataStore>specificComponent(new MockPersistentDataStore()))
            )
        .events(Components.noEvents())
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertThat(client.getDataStoreStatusProvider().isStatusMonitoringEnabled(), equalTo(true));
    }
  }

  @Test
  public void dataStoreStatusProviderReturnsLatestStatus() throws Exception {
    ComponentConfigurer<DataStore> underlyingStoreFactory = Components.persistentDataStore(
        TestComponents.<PersistentDataStore>specificComponent(new MockPersistentDataStore()));
    ContextCapturingFactory<DataStore> capturingFactory = new ContextCapturingFactory<>(underlyingStoreFactory);
    LDConfig config = baseConfig()
        .dataStore(capturingFactory)
        .build();    
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      DataStoreStatusProvider.Status originalStatus = new DataStoreStatusProvider.Status(true, false);
      DataStoreStatusProvider.Status newStatus = new DataStoreStatusProvider.Status(false, false);
      assertThat(client.getDataStoreStatusProvider().getStatus(), equalTo(originalStatus));
      capturingFactory.clientContext.getDataStoreUpdateSink().updateStatus(newStatus);
      assertThat(client.getDataStoreStatusProvider().getStatus(), equalTo(newStatus));
    }
  }

  @Test
  public void dataStoreStatusProviderSendsStatusUpdates() throws Exception {
    ComponentConfigurer<DataStore> underlyingStoreFactory = Components.persistentDataStore(
        TestComponents.<PersistentDataStore>specificComponent(new MockPersistentDataStore()));
    ContextCapturingFactory<DataStore> capturingFactory = new ContextCapturingFactory<>(underlyingStoreFactory);
    LDConfig config = baseConfig()
        .dataStore(capturingFactory)
        .build();    
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      BlockingQueue<DataStoreStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
      client.getDataStoreStatusProvider().addStatusListener(statuses::add);

      DataStoreStatusProvider.Status newStatus = new DataStoreStatusProvider.Status(false, false);
      capturingFactory.clientContext.getDataStoreUpdateSink().updateStatus(newStatus);
      
      assertThat(statuses.take(), equalTo(newStatus));
    }
  }
  
  @Test
  public void eventsAreDispatchedOnTaskThread() throws Exception {
    int desiredPriority = Thread.MAX_PRIORITY - 1;
    BlockingQueue<Thread> capturedThreads = new LinkedBlockingQueue<>();
    
    TestData testData = TestData.dataSource();
    testData.update(testData.flag("flagkey").on(true));
    LDConfig config = baseConfig()
        .dataSource(testData)
        .events(Components.noEvents())
        .threadPriority(desiredPriority)
        .build();
    
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      client.getFlagTracker().addFlagChangeListener(params -> {
        capturedThreads.add(Thread.currentThread());
      });
      
      testData.update(testData.flag("flagkey").on(false));
      
      Thread handlerThread = capturedThreads.take();
      
      assertEquals(desiredPriority, handlerThread.getPriority());
      assertThat(handlerThread.getName(), containsString("LaunchDarkly-tasks"));
    }
  }

  @Test
  public void bigSegmentStoreStatusReturnsUnavailableStatusWhenNotConfigured() throws Exception {
    LDConfig config = baseConfig().build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      BigSegmentStoreStatusProvider.Status status = client.getBigSegmentStoreStatusProvider().getStatus();
      assertFalse(status.isAvailable());
      assertFalse(status.isStale());
    }
  }

  @Test
  public void bigSegmentStoreStatusProviderSendsStatusUpdates() throws Exception {
    EasyMockSupport mocks = new EasyMockSupport(); 
    AtomicBoolean storeAvailable = new AtomicBoolean(true);
    BigSegmentStore storeMock = mocks.niceMock(BigSegmentStore.class);
    expect(storeMock.getMetadata()).andAnswer(() -> {
      if (storeAvailable.get()) {
        return new BigSegmentStoreTypes.StoreMetadata(System.currentTimeMillis());
      }
      throw new RuntimeException("sorry");
    }).anyTimes();

    ComponentConfigurer<BigSegmentStore> storeFactory = specificComponent(storeMock);

    replay(storeMock);

    LDConfig config = baseConfig()
        .bigSegments(
            Components.bigSegments(storeFactory).statusPollInterval(Duration.ofMillis(10))
        )
        .build();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      BigSegmentStoreStatusProvider.Status status1 = client.getBigSegmentStoreStatusProvider().getStatus();
      assertTrue(status1.isAvailable());

      BlockingQueue<BigSegmentStoreStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
      client.getBigSegmentStoreStatusProvider().addStatusListener(statuses::add);

      storeAvailable.set(false);
      BigSegmentStoreStatusProvider.Status status = statuses.take();
      assertFalse(status.isAvailable());
      assertEquals(status, client.getBigSegmentStoreStatusProvider().getStatus());
    }
  }
}
