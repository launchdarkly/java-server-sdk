package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.TestComponents.DataSourceFactoryThatExposesUpdater;
import com.launchdarkly.sdk.server.TestComponents.DataStoreFactoryThatExposesUpdater;
import com.launchdarkly.sdk.server.TestUtil.FlagChangeEventSink;
import com.launchdarkly.sdk.server.TestUtil.FlagValueChangeEventSink;
import com.launchdarkly.sdk.server.integrations.MockPersistentDataStore;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreFactory;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;

import org.easymock.EasyMockSupport;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestComponents.initedDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificPersistentDataStore;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * This file contains tests for all of the event broadcaster/listener functionality in the client, plus
 * related methods for looking at the same kinds of status values that can be broadcast to listeners.
 * <p>
 * Parts of this functionality are also covered by lower-level component tests like
 * DataStoreUpdatesImplTest. However, the tests here verify that the client is wiring the components
 * together correctly so that they work from an application's point of view.
 */
@SuppressWarnings("javadoc")
public class LDClientListenersTest extends EasyMockSupport {
  private final static String SDK_KEY = "SDK_KEY";

  @Test
  public void clientSendsFlagChangeEvents() throws Exception {
    DataStore testDataStore = initedDataStore();
    DataBuilder initialData = new DataBuilder().addAny(DataModel.FEATURES,
        flagBuilder("flagkey").version(1).build());
    DataSourceFactoryThatExposesUpdater updatableSource = new DataSourceFactoryThatExposesUpdater(initialData.build());
    LDConfig config = new LDConfig.Builder()
        .dataStore(specificDataStore(testDataStore))
        .dataSource(updatableSource)
        .events(Components.noEvents())
        .build();
    
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      FlagChangeEventSink eventSink1 = new FlagChangeEventSink();
      FlagChangeEventSink eventSink2 = new FlagChangeEventSink();
      client.registerFlagChangeListener(eventSink1);
      client.registerFlagChangeListener(eventSink2);
      
      eventSink1.expectNoEvents();
      eventSink2.expectNoEvents();
      
      updatableSource.updateFlag(flagBuilder("flagkey").version(2).build());
      
      FlagChangeEvent event1 = eventSink1.awaitEvent();
      FlagChangeEvent event2 = eventSink2.awaitEvent();
      assertThat(event1.getKey(), equalTo("flagkey"));
      assertThat(event2.getKey(), equalTo("flagkey"));
      eventSink1.expectNoEvents();
      eventSink2.expectNoEvents();
      
      client.unregisterFlagChangeListener(eventSink1);
      
      updatableSource.updateFlag(flagBuilder("flagkey").version(3).build());
  
      FlagChangeEvent event3 = eventSink2.awaitEvent();
      assertThat(event3.getKey(), equalTo("flagkey"));
      eventSink1.expectNoEvents();
      eventSink2.expectNoEvents();
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
      FlagValueChangeEventSink eventSink1 = new FlagValueChangeEventSink();
      FlagValueChangeEventSink eventSink2 = new FlagValueChangeEventSink();
      client.registerFlagChangeListener(Components.flagValueMonitoringListener(client, flagKey, user, eventSink1));
      client.registerFlagChangeListener(Components.flagValueMonitoringListener(client, flagKey, otherUser, eventSink2));
      
      eventSink1.expectNoEvents();
      eventSink2.expectNoEvents();
      
      FeatureFlag flagIsTrueForMyUserOnly = flagBuilder(flagKey).version(2).on(true).variations(false, true)
          .targets(ModelBuilders.target(1, user.getKey())).fallthroughVariation(0).build();
      updatableSource.updateFlag(flagIsTrueForMyUserOnly);
      
      // eventSink1 receives a value change event; eventSink2 doesn't because the flag's value hasn't changed for otherUser
      FlagValueChangeEvent event1 = eventSink1.awaitEvent();
      assertThat(event1.getKey(), equalTo(flagKey));
      assertThat(event1.getOldValue(), equalTo(LDValue.of(false)));
      assertThat(event1.getNewValue(), equalTo(LDValue.of(true)));
      eventSink1.expectNoEvents();
      
      eventSink2.expectNoEvents();
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
      assertThat(client.getDataStoreStatusProvider().getStoreStatus(), equalTo(originalStatus));
      factoryWithUpdater.statusUpdater.accept(newStatus);
      assertThat(client.getDataStoreStatusProvider().getStoreStatus(), equalTo(newStatus));
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
      final BlockingQueue<DataStoreStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
      client.getDataStoreStatusProvider().addStatusListener(statuses::add);

      DataStoreStatusProvider.Status newStatus = new DataStoreStatusProvider.Status(false, false);
      factoryWithUpdater.statusUpdater.accept(newStatus);
      
      assertThat(statuses.take(), equalTo(newStatus));
    }
  }
}
