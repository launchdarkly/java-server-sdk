package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.TestComponents.DataSourceFactoryThatExposesUpdater;
import com.launchdarkly.sdk.server.TestComponents.DataStoreFactoryThatExposesUpdater;
import com.launchdarkly.sdk.server.integrations.MockPersistentDataStore;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreFactory;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;

import org.easymock.EasyMockSupport;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestComponents.initedDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificPersistentDataStore;
import static com.launchdarkly.sdk.server.TestUtil.awaitValue;
import static com.launchdarkly.sdk.server.TestUtil.expectNoMoreValues;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

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
public class LDClientListenersTest extends EasyMockSupport {
  private final static String SDK_KEY = "SDK_KEY";

  @Test
  public void clientSendsFlagChangeEvents() throws Exception {
    String flagKey = "flagkey";
    DataStore testDataStore = initedDataStore();
    DataBuilder initialData = new DataBuilder().addAny(DataModel.FEATURES,
        flagBuilder(flagKey).version(1).build());
    DataSourceFactoryThatExposesUpdater updatableSource = new DataSourceFactoryThatExposesUpdater(initialData.build());
    LDConfig config = new LDConfig.Builder()
        .dataStore(specificDataStore(testDataStore))
        .dataSource(updatableSource)
        .events(Components.noEvents())
        .build();
    
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      BlockingQueue<FlagChangeEvent> eventSink1 = new LinkedBlockingQueue<>();
      BlockingQueue<FlagChangeEvent> eventSink2 = new LinkedBlockingQueue<>();
      FlagChangeListener listener1 = eventSink1::add;
      FlagChangeListener listener2 = eventSink2::add; // need to capture the method reference in a variable so it's the same instance when we unregister it
      client.getFlagTracker().addFlagChangeListener(listener1);
      client.getFlagTracker().addFlagChangeListener(listener2);
      
      expectNoMoreValues(eventSink1, Duration.ofMillis(100));
      expectNoMoreValues(eventSink2, Duration.ofMillis(100));
      
      updatableSource.updateFlag(flagBuilder(flagKey).version(2).build());
      
      FlagChangeEvent event1 = awaitValue(eventSink1, Duration.ofSeconds(1));
      FlagChangeEvent event2 = awaitValue(eventSink2, Duration.ofSeconds(1));
      assertThat(event1.getKey(), equalTo(flagKey));
      assertThat(event2.getKey(), equalTo(flagKey));
      expectNoMoreValues(eventSink1, Duration.ofMillis(100));
      expectNoMoreValues(eventSink2, Duration.ofMillis(100));
      
      client.getFlagTracker().removeFlagChangeListener(listener1);
      
      updatableSource.updateFlag(flagBuilder(flagKey).version(3).build());
  
      FlagChangeEvent event3 = awaitValue(eventSink2, Duration.ofSeconds(1));
      assertThat(event3.getKey(), equalTo(flagKey));
      expectNoMoreValues(eventSink1, Duration.ofMillis(100));
      expectNoMoreValues(eventSink2, Duration.ofMillis(100));
    }
  }

  @Test
  public void clientSendsFlagValueChangeEvents() throws Exception {
    String flagKey = "important-flag";
    LDUser user = new LDUser("important-user");
    LDUser otherUser = new LDUser("unimportant-user");
    DataStore testDataStore = initedDataStore();
    
    FeatureFlag alwaysFalseFlag = flagBuilder(flagKey).version(1).on(true).variations(false, true)
        .fallthroughVariation(0).build();
    DataBuilder initialData = new DataBuilder().addAny(DataModel.FEATURES, alwaysFalseFlag);
    
    DataSourceFactoryThatExposesUpdater updatableSource = new DataSourceFactoryThatExposesUpdater(initialData.build());
    LDConfig config = new LDConfig.Builder()
        .dataStore(specificDataStore(testDataStore))
        .dataSource(updatableSource)
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
      
      expectNoMoreValues(eventSink1, Duration.ofMillis(100));
      expectNoMoreValues(eventSink2, Duration.ofMillis(100));
      expectNoMoreValues(eventSink3, Duration.ofMillis(100));
      
      // make the flag true for the first user only, and broadcast a flag change event
      FeatureFlag flagIsTrueForMyUserOnly = flagBuilder(flagKey).version(2).on(true).variations(false, true)
          .targets(ModelBuilders.target(1, user.getKey())).fallthroughVariation(0).build();
      updatableSource.updateFlag(flagIsTrueForMyUserOnly);
      
      // eventSink1 receives a value change event
      FlagValueChangeEvent event1 = awaitValue(eventSink1, Duration.ofSeconds(1));
      assertThat(event1.getKey(), equalTo(flagKey));
      assertThat(event1.getOldValue(), equalTo(LDValue.of(false)));
      assertThat(event1.getNewValue(), equalTo(LDValue.of(true)));
      expectNoMoreValues(eventSink1, Duration.ofMillis(100));
      
      // eventSink2 doesn't receive one, because it was unregistered
      expectNoMoreValues(eventSink2, Duration.ofMillis(100));
      
      // eventSink3 doesn't receive one, because the flag's value hasn't changed for otherUser
      expectNoMoreValues(eventSink2, Duration.ofMillis(100));
    }
  }
  
  @Test
  public void dataSourceStatusProviderReturnsLatestStatus() throws Exception {
    DataSourceFactoryThatExposesUpdater updatableSource = new DataSourceFactoryThatExposesUpdater(new DataBuilder().build());
    LDConfig config = new LDConfig.Builder()
        .dataSource(updatableSource)
        .events(Components.noEvents())
        .build();

    Instant timeBeforeStarting = Instant.now();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      DataSourceStatusProvider.Status initialStatus = client.getDataSourceStatusProvider().getStatus();
      assertThat(initialStatus.getState(), equalTo(DataSourceStatusProvider.State.INITIALIZING));
      assertThat(initialStatus.getStateSince(), greaterThanOrEqualTo(timeBeforeStarting));
      assertThat(initialStatus.getLastError(), nullValue());
      
      DataSourceStatusProvider.ErrorInfo errorInfo = new DataSourceStatusProvider.ErrorInfo(
          DataSourceStatusProvider.ErrorKind.ERROR_RESPONSE, 401, null, Instant.now());
      updatableSource.dataSourceUpdates.updateStatus(DataSourceStatusProvider.State.OFF, errorInfo);

      DataSourceStatusProvider.Status newStatus = client.getDataSourceStatusProvider().getStatus();
      assertThat(newStatus.getState(), equalTo(DataSourceStatusProvider.State.OFF));
      assertThat(newStatus.getStateSince(), greaterThanOrEqualTo(errorInfo.getTime()));
      assertThat(newStatus.getLastError(), equalTo(errorInfo));
    }
  }

  @Test
  public void dataSourceStatusProviderSendsStatusUpdates() throws Exception {
    DataSourceFactoryThatExposesUpdater updatableSource = new DataSourceFactoryThatExposesUpdater(new DataBuilder().build());
    LDConfig config = new LDConfig.Builder()
        .dataSource(updatableSource)
        .events(Components.noEvents())
        .build();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      BlockingQueue<DataSourceStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
      client.getDataSourceStatusProvider().addStatusListener(statuses::add);

      DataSourceStatusProvider.ErrorInfo errorInfo = new DataSourceStatusProvider.ErrorInfo(
          DataSourceStatusProvider.ErrorKind.ERROR_RESPONSE, 401, null, Instant.now());
      updatableSource.dataSourceUpdates.updateStatus(DataSourceStatusProvider.State.OFF, errorInfo);

      DataSourceStatusProvider.Status newStatus = statuses.take();
      assertThat(newStatus.getState(), equalTo(DataSourceStatusProvider.State.OFF));
      assertThat(newStatus.getStateSince(), greaterThanOrEqualTo(errorInfo.getTime()));
      assertThat(newStatus.getLastError(), equalTo(errorInfo));
    }
  }
  
  @Test
  public void dataStoreStatusMonitoringIsDisabledForInMemoryStore() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .events(Components.noEvents())
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertThat(client.getDataStoreStatusProvider().isStatusMonitoringEnabled(), equalTo(false));
    }
  }

  @Test
  public void dataStoreStatusMonitoringIsEnabledForPersistentStore() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .dataStore(
            Components.persistentDataStore(specificPersistentDataStore(new MockPersistentDataStore()))
            )
        .events(Components.noEvents())
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertThat(client.getDataStoreStatusProvider().isStatusMonitoringEnabled(), equalTo(true));
    }
  }

  @Test
  public void dataStoreStatusProviderReturnsLatestStatus() throws Exception {
    DataStoreFactory underlyingStoreFactory = Components.persistentDataStore(
        specificPersistentDataStore(new MockPersistentDataStore()));
    DataStoreFactoryThatExposesUpdater factoryWithUpdater = new DataStoreFactoryThatExposesUpdater(underlyingStoreFactory);
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .dataStore(factoryWithUpdater)
        .events(Components.noEvents())
        .build();    
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      DataStoreStatusProvider.Status originalStatus = new DataStoreStatusProvider.Status(true, false);
      DataStoreStatusProvider.Status newStatus = new DataStoreStatusProvider.Status(false, false);
      assertThat(client.getDataStoreStatusProvider().getStatus(), equalTo(originalStatus));
      factoryWithUpdater.dataStoreUpdates.updateStatus(newStatus);
      assertThat(client.getDataStoreStatusProvider().getStatus(), equalTo(newStatus));
    }
  }

  @Test
  public void dataStoreStatusProviderSendsStatusUpdates() throws Exception {
    DataStoreFactory underlyingStoreFactory = Components.persistentDataStore(
        specificPersistentDataStore(new MockPersistentDataStore()));
    DataStoreFactoryThatExposesUpdater factoryWithUpdater = new DataStoreFactoryThatExposesUpdater(underlyingStoreFactory);
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .dataStore(factoryWithUpdater)
        .events(Components.noEvents())
        .build();    
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      BlockingQueue<DataStoreStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
      client.getDataStoreStatusProvider().addStatusListener(statuses::add);

      DataStoreStatusProvider.Status newStatus = new DataStoreStatusProvider.Status(false, false);
      factoryWithUpdater.dataStoreUpdates.updateStatus(newStatus);
      
      assertThat(statuses.take(), equalTo(newStatus));
    }
  }
  
  @Test
  public void eventsAreDispatchedOnTaskThread() throws Exception {
    int desiredPriority = Thread.MAX_PRIORITY - 1;
    BlockingQueue<Thread> capturedThreads = new LinkedBlockingQueue<>();
    
    DataStore testDataStore = initedDataStore();
    DataBuilder initialData = new DataBuilder().addAny(DataModel.FEATURES,
        flagBuilder("flagkey").version(1).build());
    DataSourceFactoryThatExposesUpdater updatableSource = new DataSourceFactoryThatExposesUpdater(initialData.build());
    LDConfig config = new LDConfig.Builder()
        .dataStore(specificDataStore(testDataStore))
        .dataSource(updatableSource)
        .events(Components.noEvents())
        .threadPriority(desiredPriority)
        .build();
    
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      client.getFlagTracker().addFlagChangeListener(params -> {
        capturedThreads.add(Thread.currentThread());
      });
      
      updatableSource.updateFlag(flagBuilder("flagkey").version(2).build());
      
      Thread handlerThread = capturedThreads.take();
      
      assertEquals(desiredPriority, handlerThread.getPriority());
      assertThat(handlerThread.getName(), containsString("LaunchDarkly-tasks"));
    }
  }
}
