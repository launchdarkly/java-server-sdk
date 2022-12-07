package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.Status;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.prerequisite;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.TestComponents.inMemoryDataStore;
import static com.launchdarkly.sdk.server.TestComponents.nullLogger;
import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static com.launchdarkly.sdk.server.TestUtil.expectEvents;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.assertNoMoreValues;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.awaitValue;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("javadoc")
public class DataSourceUpdatesImplTest {
  // Note that these tests must use the actual data model types for flags and segments, rather than the
  // TestItem type from DataStoreTestTypes, because the dependency behavior is based on the real data model.
  
  private final EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> flagChangeBroadcaster =
      EventBroadcasterImpl.forFlagChangeEvents(TestComponents.sharedExecutor, nullLogger);
  private final EasyMockSupport mocks = new EasyMockSupport();
  
  private DataSourceUpdatesImpl makeInstance(DataStore store) {
    return makeInstance(store, null);
  }

  private DataSourceUpdatesImpl makeInstance(
      DataStore store,
      EventBroadcasterImpl<DataSourceStatusProvider.StatusListener, DataSourceStatusProvider.Status> statusBroadcaster
      ) {
    return new DataSourceUpdatesImpl(store, null, flagChangeBroadcaster, statusBroadcaster, sharedExecutor, null, nullLogger);
  }
  
  @Test
  public void sendsEventsOnInitForNewlyAddedFlags() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build())
        .addAny(SEGMENTS,
            segmentBuilder("segment1").version(1).build());
        
    DataSourceUpdatesImpl storeUpdates = makeInstance(store);

    storeUpdates.init(builder.build());

    BlockingQueue<FlagChangeEvent> eventSink = new LinkedBlockingQueue<>();
    flagChangeBroadcaster.register(eventSink::add);
    
    builder.addAny(FEATURES, flagBuilder("flag2").version(1).build())
        .addAny(SEGMENTS, segmentBuilder("segment2").version(1).build());
    // the new segment triggers no events since nothing is using it
    
    storeUpdates.init(builder.build());
  
    expectEvents(eventSink, "flag2");
  }

  @Test
  public void sendsEventOnUpdateForNewlyAddedFlag() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build())
        .addAny(SEGMENTS,
            segmentBuilder("segment1").version(1).build());
    
    DataSourceUpdatesImpl storeUpdates = makeInstance(store);

    storeUpdates.init(builder.build());
    
    BlockingQueue<FlagChangeEvent> eventSink = new LinkedBlockingQueue<>();
    flagChangeBroadcaster.register(eventSink::add);
    
    storeUpdates.upsert(FEATURES, "flag2", new ItemDescriptor(1, flagBuilder("flag2").version(1).build()));
  
    expectEvents(eventSink, "flag2");
  }
  
  @Test
  public void sendsEventsOnInitForUpdatedFlags() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build(),
            flagBuilder("flag2").version(1).build())
        .addAny(SEGMENTS,
            segmentBuilder("segment1").version(1).build(),
            segmentBuilder("segment2").version(1).build());
    
    DataSourceUpdatesImpl storeUpdates = makeInstance(store);

    storeUpdates.init(builder.build());

    BlockingQueue<FlagChangeEvent> eventSink = new LinkedBlockingQueue<>();
    flagChangeBroadcaster.register(eventSink::add);
    
    builder.addAny(FEATURES, flagBuilder("flag2").version(2).build()) // modified flag
        .addAny(SEGMENTS, segmentBuilder("segment2").version(2).build()); // modified segment, but it's irrelevant
    storeUpdates.init(builder.build());
    
    expectEvents(eventSink, "flag2");
  }

  @Test
  public void sendsEventOnUpdateForUpdatedFlag() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build(),
            flagBuilder("flag2").version(1).build())
        .addAny(SEGMENTS,
            segmentBuilder("segment1").version(1).build());
    
    DataSourceUpdatesImpl storeUpdates = makeInstance(store);

    storeUpdates.init(builder.build());

    BlockingQueue<FlagChangeEvent> eventSink = new LinkedBlockingQueue<>();
    flagChangeBroadcaster.register(eventSink::add);
    
    storeUpdates.upsert(FEATURES, "flag2", new ItemDescriptor(2, flagBuilder("flag2").version(2).build()));
  
    expectEvents(eventSink, "flag2");
  }

  @Test
  public void doesNotSendsEventOnUpdateIfItemWasNotReallyUpdated() throws Exception {
    DataStore store = inMemoryDataStore();
    DataModel.FeatureFlag flag1 = flagBuilder("flag1").version(1).build();
    DataModel.FeatureFlag flag2 = flagBuilder("flag2").version(1).build();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES, flag1, flag2);
    
    DataSourceUpdatesImpl storeUpdates = makeInstance(store);

    storeUpdates.init(builder.build());

    BlockingQueue<FlagChangeEvent> eventSink = new LinkedBlockingQueue<>();
    flagChangeBroadcaster.register(eventSink::add);
    
    storeUpdates.upsert(FEATURES, flag2.getKey(), new ItemDescriptor(flag2.getVersion(), flag2));
  
    assertNoMoreValues(eventSink, 100, TimeUnit.MILLISECONDS);
  }
  
  @Test
  public void sendsEventsOnInitForDeletedFlags() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build(),
            flagBuilder("flag2").version(1).build())
        .addAny(SEGMENTS,
            segmentBuilder("segment1").version(1).build());
    
    DataSourceUpdatesImpl storeUpdates = makeInstance(store);

    storeUpdates.init(builder.build());

    BlockingQueue<FlagChangeEvent> eventSink = new LinkedBlockingQueue<>();
    flagChangeBroadcaster.register(eventSink::add);
    
    builder.remove(FEATURES, "flag2");
    builder.remove(SEGMENTS, "segment1"); // deleted segment isn't being used so it's irrelevant
    // note that the full data set for init() will never include deleted item placeholders
    
    storeUpdates.init(builder.build());
  
    expectEvents(eventSink, "flag2");
  }

  @Test
  public void sendsEventOnUpdateForDeletedFlag() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build(),
            flagBuilder("flag2").version(1).build())
        .addAny(SEGMENTS,
            segmentBuilder("segment1").version(1).build());
    
    DataSourceUpdatesImpl storeUpdates = makeInstance(store);

    storeUpdates.init(builder.build());
    
    BlockingQueue<FlagChangeEvent> events = new LinkedBlockingQueue<>();
    flagChangeBroadcaster.register(events::add);
    
    storeUpdates.upsert(FEATURES, "flag2", ItemDescriptor.deletedItem(2));
  
    expectEvents(events, "flag2");
  }

  @Test
  public void sendsEventsOnInitForFlagsWhosePrerequisitesChanged() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build(),
            flagBuilder("flag2").version(1).prerequisites(prerequisite("flag1", 0)).build(),
            flagBuilder("flag3").version(1).build(),
            flagBuilder("flag4").version(1).prerequisites(prerequisite("flag1", 0)).build(),
            flagBuilder("flag5").version(1).prerequisites(prerequisite("flag4", 0)).build(),
            flagBuilder("flag6").version(1).build());
    
    DataSourceUpdatesImpl storeUpdates = makeInstance(store);
    
    storeUpdates.init(builder.build());
    
    BlockingQueue<FlagChangeEvent> eventSink = new LinkedBlockingQueue<>();
    flagChangeBroadcaster.register(eventSink::add);
  
    builder.addAny(FEATURES, flagBuilder("flag1").version(2).build());
    storeUpdates.init(builder.build());
  
    expectEvents(eventSink, "flag1", "flag2", "flag4", "flag5");
  }

  @Test
  public void sendsEventsOnUpdateForFlagsWhosePrerequisitesChanged() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build(),
            flagBuilder("flag2").version(1).prerequisites(prerequisite("flag1", 0)).build(),
            flagBuilder("flag3").version(1).build(),
            flagBuilder("flag4").version(1).prerequisites(prerequisite("flag1", 0)).build(),
            flagBuilder("flag5").version(1).prerequisites(prerequisite("flag4", 0)).build(),
            flagBuilder("flag6").version(1).build());
    
    DataSourceUpdatesImpl storeUpdates = makeInstance(store);

    storeUpdates.init(builder.build());
    
    BlockingQueue<FlagChangeEvent> eventSink = new LinkedBlockingQueue<>();
    flagChangeBroadcaster.register(eventSink::add);
    
    storeUpdates.upsert(FEATURES, "flag1", new ItemDescriptor(2, flagBuilder("flag1").version(2).build()));
  
    expectEvents(eventSink, "flag1", "flag2", "flag4", "flag5");
  }

  @Test
  public void sendsEventsOnInitForFlagsWhoseSegmentsChanged() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build(),
            flagBuilder("flag2").version(1).rules(
                ruleBuilder().clauses(
                    ModelBuilders.clauseMatchingSegment("segment1")
                    ).build()
                ).build(),
            flagBuilder("flag3").version(1).build(),
            flagBuilder("flag4").version(1).prerequisites(prerequisite("flag2", 0)).build())
        .addAny(SEGMENTS,
            segmentBuilder("segment1").version(1).build(),
            segmentBuilder("segment2").version(1).build());
    
    DataSourceUpdatesImpl storeUpdates = makeInstance(store);

    storeUpdates.init(builder.build());
    
    BlockingQueue<FlagChangeEvent> eventSink = new LinkedBlockingQueue<>();
    flagChangeBroadcaster.register(eventSink::add);
    
    storeUpdates.upsert(SEGMENTS, "segment1", new ItemDescriptor(2, segmentBuilder("segment1").version(2).build()));
  
    expectEvents(eventSink, "flag2", "flag4");
  }
  
  @Test
  public void sendsEventsOnUpdateForFlagsWhoseSegmentsChanged() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build(),
            flagBuilder("flag2").version(1).rules(
                ruleBuilder().clauses(
                    ModelBuilders.clauseMatchingSegment("segment1")
                    ).build()
                ).build(),
            flagBuilder("flag3").version(1).build(),
            flagBuilder("flag4").version(1).prerequisites(prerequisite("flag2", 0)).build())
        .addAny(SEGMENTS,
            segmentBuilder("segment1").version(1).build(),
            segmentBuilder("segment2").version(1).build());

    DataSourceUpdatesImpl storeUpdates = makeInstance(store);

    storeUpdates.init(builder.build());
    
    BlockingQueue<FlagChangeEvent> eventSink = new LinkedBlockingQueue<>();
    flagChangeBroadcaster.register(eventSink::add);
    
    builder.addAny(SEGMENTS, segmentBuilder("segment1").version(2).build());
    storeUpdates.init(builder.build());
    
    expectEvents(eventSink, "flag2", "flag4");
  }

  @Test
  public void dataSetIsPassedToDataStoreInCorrectOrder() throws Exception {
    // The logic for this is already tested in DataModelDependenciesTest, but here we are verifying
    // that DataSourceUpdatesImpl is actually using DataModelDependencies.
    Capture<FullDataSet<ItemDescriptor>> captureData = Capture.newInstance();
    DataStore store = mocks.createStrictMock(DataStore.class);
    store.init(EasyMock.capture(captureData));
    replay(store);
    
    DataSourceUpdatesImpl storeUpdates = makeInstance(store);
    storeUpdates.init(DataModelDependenciesTest.DEPENDENCY_ORDERING_TEST_DATA);
    
    DataModelDependenciesTest.verifySortedData(captureData.getValue(),
        DataModelDependenciesTest.DEPENDENCY_ORDERING_TEST_DATA);

  }

  @Test
  public void updateStatusBroadcastsNewStatus() {
    EventBroadcasterImpl<DataSourceStatusProvider.StatusListener, DataSourceStatusProvider.Status> broadcaster =
        EventBroadcasterImpl.forDataSourceStatus(sharedExecutor, nullLogger);
    DataSourceUpdatesImpl updates = makeInstance(inMemoryDataStore(), broadcaster);
    
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    broadcaster.register(statuses::add);
    
    Instant timeBeforeUpdate = Instant.now();
    ErrorInfo errorInfo = ErrorInfo.fromHttpError(401);
    updates.updateStatus(State.OFF, errorInfo);
    
    Status status = awaitValue(statuses, 500, TimeUnit.MILLISECONDS);
    
    assertThat(status.getState(), is(State.OFF));
    assertThat(status.getStateSince(), greaterThanOrEqualTo(timeBeforeUpdate));
    assertThat(status.getLastError(), is(errorInfo));
  }

  @Test
  public void updateStatusKeepsStateUnchangedIfStateWasInitializingAndNewStateIsInterrupted() {
    EventBroadcasterImpl<DataSourceStatusProvider.StatusListener, DataSourceStatusProvider.Status> broadcaster =
        EventBroadcasterImpl.forDataSourceStatus(sharedExecutor, nullLogger);
    DataSourceUpdatesImpl updates = makeInstance(inMemoryDataStore(), broadcaster);
    
    assertThat(updates.getLastStatus().getState(), is(State.INITIALIZING));
    Instant originalTime = updates.getLastStatus().getStateSince();
    
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    broadcaster.register(statuses::add);
    
    ErrorInfo errorInfo = ErrorInfo.fromHttpError(401);
    updates.updateStatus(State.INTERRUPTED, errorInfo);
    
    Status status = awaitValue(statuses, 500, TimeUnit.MILLISECONDS);
    
    assertThat(status.getState(), is(State.INITIALIZING));
    assertThat(status.getStateSince(), is(originalTime));
    assertThat(status.getLastError(), is(errorInfo));
  }

  @Test
  public void updateStatusDoesNothingIfParametersHaveNoNewData() {
    EventBroadcasterImpl<DataSourceStatusProvider.StatusListener, DataSourceStatusProvider.Status> broadcaster =
        EventBroadcasterImpl.forDataSourceStatus(sharedExecutor, nullLogger);
    DataSourceUpdatesImpl updates = makeInstance(inMemoryDataStore(), broadcaster);
    
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    broadcaster.register(statuses::add);
    
    updates.updateStatus(null, null);
    updates.updateStatus(State.INITIALIZING, null);

    assertNoMoreValues(statuses, 100, TimeUnit.MILLISECONDS);
  }
  
  @Test
  public void outageTimeoutLogging() throws Exception {
    BlockingQueue<String> outageErrors = new LinkedBlockingQueue<>();
    Duration outageTimeout = Duration.ofMillis(100);
    
    DataSourceUpdatesImpl updates = new DataSourceUpdatesImpl(
        inMemoryDataStore(),
        null,
        flagChangeBroadcaster,
        EventBroadcasterImpl.forDataSourceStatus(sharedExecutor, nullLogger),
        sharedExecutor,
        outageTimeout,
        nullLogger
    );
    updates.onOutageErrorLog = outageErrors::add;
    
    // simulate an outage
    updates.updateStatus(State.INTERRUPTED, ErrorInfo.fromHttpError(500));
    
    // but recover from it immediately
    updates.updateStatus(State.VALID, null);
    
    // wait till the timeout would have elapsed - no special message should be logged
    assertNoMoreValues(outageErrors, outageTimeout.plus(Duration.ofMillis(20)).toMillis(), TimeUnit.MILLISECONDS);
    
    // simulate another outage
    updates.updateStatus(State.INTERRUPTED, ErrorInfo.fromHttpError(501));
    updates.updateStatus(State.INTERRUPTED, ErrorInfo.fromHttpError(502));
    updates.updateStatus(State.INTERRUPTED, ErrorInfo.fromException(ErrorKind.NETWORK_ERROR, new IOException("x")));
    updates.updateStatus(State.INTERRUPTED, ErrorInfo.fromHttpError(501));
    
    String errorsDesc = awaitValue(outageErrors, 250, TimeUnit.MILLISECONDS); // timing is approximate
    assertThat(errorsDesc, containsString("NETWORK_ERROR (1 time)"));
    assertThat(errorsDesc, containsString("ERROR_RESPONSE(501) (2 times)"));
    assertThat(errorsDesc, containsString("ERROR_RESPONSE(502) (1 time)"));
  }
}
