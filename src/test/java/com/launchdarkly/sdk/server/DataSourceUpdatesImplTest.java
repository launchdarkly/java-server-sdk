package com.launchdarkly.sdk.server;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.TestUtil.FlagChangeEventSink;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.google.common.collect.Iterables.transform;
import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toDataMap;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.prerequisite;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.TestComponents.inMemoryDataStore;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@SuppressWarnings("javadoc")
public class DataSourceUpdatesImplTest extends EasyMockSupport {
  // Note that these tests must use the actual data model types for flags and segments, rather than the
  // TestItem type from DataStoreTestTypes, because the dependency behavior is based on the real data model.
  
  private EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> flagChangeBroadcaster =
      EventBroadcasterImpl.forFlagChangeEvents(TestComponents.sharedExecutor);
  
  @Test
  public void sendsEventsOnInitForNewlyAddedFlags() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build())
        .addAny(SEGMENTS,
            segmentBuilder("segment1").version(1).build());
        
    DataSourceUpdatesImpl storeUpdates = new DataSourceUpdatesImpl(store, null, flagChangeBroadcaster, null);

    storeUpdates.init(builder.build());

    FlagChangeEventSink eventSink = new FlagChangeEventSink();
    flagChangeBroadcaster.register(eventSink);
    
    builder.addAny(FEATURES, flagBuilder("flag2").version(1).build())
        .addAny(SEGMENTS, segmentBuilder("segment2").version(1).build());
    // the new segment triggers no events since nothing is using it
    
    storeUpdates.init(builder.build());
  
    eventSink.expectEvents("flag2");
  }

  @Test
  public void sendsEventOnUpdateForNewlyAddedFlag() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build())
        .addAny(SEGMENTS,
            segmentBuilder("segment1").version(1).build());
    
    DataSourceUpdatesImpl storeUpdates = new DataSourceUpdatesImpl(store, null, flagChangeBroadcaster, null);

    storeUpdates.init(builder.build());
    
    FlagChangeEventSink eventSink = new FlagChangeEventSink();
    flagChangeBroadcaster.register(eventSink);
    
    storeUpdates.upsert(FEATURES, "flag2", new ItemDescriptor(1, flagBuilder("flag2").version(1).build()));
  
    eventSink.expectEvents("flag2");
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
    
    DataSourceUpdatesImpl storeUpdates = new DataSourceUpdatesImpl(store, null, flagChangeBroadcaster, null);

    storeUpdates.init(builder.build());

    FlagChangeEventSink eventSink = new FlagChangeEventSink();
    flagChangeBroadcaster.register(eventSink);
    
    builder.addAny(FEATURES, flagBuilder("flag2").version(2).build()) // modified flag
        .addAny(SEGMENTS, segmentBuilder("segment2").version(2).build()); // modified segment, but it's irrelevant
    storeUpdates.init(builder.build());
    
    eventSink.expectEvents("flag2");
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
    
    DataSourceUpdatesImpl storeUpdates = new DataSourceUpdatesImpl(store, null, flagChangeBroadcaster, null);

    storeUpdates.init(builder.build());

    FlagChangeEventSink eventSink = new FlagChangeEventSink();
    flagChangeBroadcaster.register(eventSink);
    
    storeUpdates.upsert(FEATURES, "flag2", new ItemDescriptor(2, flagBuilder("flag2").version(2).build()));
  
    eventSink.expectEvents("flag2");
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
    
    DataSourceUpdatesImpl storeUpdates = new DataSourceUpdatesImpl(store, null, flagChangeBroadcaster, null);

    storeUpdates.init(builder.build());

    FlagChangeEventSink eventSink = new FlagChangeEventSink();
    flagChangeBroadcaster.register(eventSink);
    
    builder.remove(FEATURES, "flag2");
    builder.remove(SEGMENTS, "segment1"); // deleted segment isn't being used so it's irrelevant
    // note that the full data set for init() will never include deleted item placeholders
    
    storeUpdates.init(builder.build());
  
    eventSink.expectEvents("flag2");
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
    
    DataSourceUpdatesImpl storeUpdates = new DataSourceUpdatesImpl(store, null, flagChangeBroadcaster, null);

    storeUpdates.init(builder.build());
    
    FlagChangeEventSink eventSink = new FlagChangeEventSink();
    flagChangeBroadcaster.register(eventSink);
    
    storeUpdates.upsert(FEATURES, "flag2", ItemDescriptor.deletedItem(2));
  
    eventSink.expectEvents("flag2");
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
    
    DataSourceUpdatesImpl storeUpdates = new DataSourceUpdatesImpl(store, null, flagChangeBroadcaster, null);
    
    storeUpdates.init(builder.build());
    
    FlagChangeEventSink eventSink = new FlagChangeEventSink();
    flagChangeBroadcaster.register(eventSink);
  
    builder.addAny(FEATURES, flagBuilder("flag1").version(2).build());
    storeUpdates.init(builder.build());
  
    eventSink.expectEvents("flag1", "flag2", "flag4", "flag5");
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
    
    DataSourceUpdatesImpl storeUpdates = new DataSourceUpdatesImpl(store, null, flagChangeBroadcaster, null);

    storeUpdates.init(builder.build());
    
    FlagChangeEventSink eventSink = new FlagChangeEventSink();
    flagChangeBroadcaster.register(eventSink);
    
    storeUpdates.upsert(FEATURES, "flag1", new ItemDescriptor(2, flagBuilder("flag1").version(2).build()));
  
    eventSink.expectEvents("flag1", "flag2", "flag4", "flag5");
  }

  @Test
  public void sendsEventsOnInitForFlagsWhoseSegmentsChanged() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build(),
            flagBuilder("flag2").version(1).rules(
                ruleBuilder().clauses(
                    ModelBuilders.clause(null, Operator.segmentMatch, LDValue.of("segment1"))
                    ).build()
                ).build(),
            flagBuilder("flag3").version(1).build(),
            flagBuilder("flag4").version(1).prerequisites(prerequisite("flag2", 0)).build())
        .addAny(SEGMENTS,
            segmentBuilder("segment1").version(1).build(),
            segmentBuilder("segment2").version(1).build());
    
    DataSourceUpdatesImpl storeUpdates = new DataSourceUpdatesImpl(store, null, flagChangeBroadcaster, null);

    storeUpdates.init(builder.build());
    
    FlagChangeEventSink eventSink = new FlagChangeEventSink();
    flagChangeBroadcaster.register(eventSink);
    
    storeUpdates.upsert(SEGMENTS, "segment1", new ItemDescriptor(2, segmentBuilder("segment1").version(2).build()));
  
    eventSink.expectEvents("flag2", "flag4");
  }
  
  @Test
  public void sendsEventsOnUpdateForFlagsWhoseSegmentsChanged() throws Exception {
    DataStore store = inMemoryDataStore();
    DataBuilder builder = new DataBuilder()
        .addAny(FEATURES,
            flagBuilder("flag1").version(1).build(),
            flagBuilder("flag2").version(1).rules(
                ruleBuilder().clauses(
                    ModelBuilders.clause(null, Operator.segmentMatch, LDValue.of("segment1"))
                    ).build()
                ).build(),
            flagBuilder("flag3").version(1).build(),
            flagBuilder("flag4").version(1).prerequisites(prerequisite("flag2", 0)).build())
        .addAny(SEGMENTS,
            segmentBuilder("segment1").version(1).build(),
            segmentBuilder("segment2").version(1).build());

    DataSourceUpdatesImpl storeUpdates = new DataSourceUpdatesImpl(store, null, flagChangeBroadcaster, null);

    storeUpdates.init(builder.build());
    
    FlagChangeEventSink eventSink = new FlagChangeEventSink();
    flagChangeBroadcaster.register(eventSink);
    
    builder.addAny(SEGMENTS, segmentBuilder("segment1").version(2).build());
    storeUpdates.init(builder.build());
    
    eventSink.expectEvents("flag2", "flag4");
  }

  @Test
  public void dataSetIsPassedToDataStoreInCorrectOrder() throws Exception {
    // This verifies that the client is using DataStoreClientWrapper and that it is applying the
    // correct ordering for flag prerequisites, etc. This should work regardless of what kind of
    // DataSource we're using.
    
    Capture<FullDataSet<ItemDescriptor>> captureData = Capture.newInstance();
    DataStore store = createStrictMock(DataStore.class);
    store.init(EasyMock.capture(captureData));
    replay(store);
    
    DataSourceUpdatesImpl storeUpdates = new DataSourceUpdatesImpl(store, null, flagChangeBroadcaster, null);
    storeUpdates.init(DEPENDENCY_ORDERING_TEST_DATA);
       
    Map<DataKind, Map<String, ItemDescriptor>> dataMap = toDataMap(captureData.getValue());
    assertEquals(2, dataMap.size());
    Map<DataKind, Map<String, ItemDescriptor>> inputDataMap = toDataMap(DEPENDENCY_ORDERING_TEST_DATA);
    
    // Segments should always come first
    assertEquals(SEGMENTS, Iterables.get(dataMap.keySet(), 0));
    assertEquals(inputDataMap.get(SEGMENTS).size(), Iterables.get(dataMap.values(), 0).size());
    
    // Features should be ordered so that a flag always appears after its prerequisites, if any
    assertEquals(FEATURES, Iterables.get(dataMap.keySet(), 1));
    Map<String, ItemDescriptor> map1 = Iterables.get(dataMap.values(), 1);
    List<DataModel.FeatureFlag> list1 = ImmutableList.copyOf(transform(map1.values(), d -> (DataModel.FeatureFlag)d.getItem()));
    assertEquals(inputDataMap.get(FEATURES).size(), map1.size());
    for (int itemIndex = 0; itemIndex < list1.size(); itemIndex++) {
      DataModel.FeatureFlag item = list1.get(itemIndex);
      for (DataModel.Prerequisite prereq: item.getPrerequisites()) {
        DataModel.FeatureFlag depFlag = (DataModel.FeatureFlag)map1.get(prereq.getKey()).getItem();
        int depIndex = list1.indexOf(depFlag);
        if (depIndex > itemIndex) {
          fail(String.format("%s depends on %s, but %s was listed first; keys in order are [%s]",
              item.getKey(), prereq.getKey(), item.getKey(),
              Joiner.on(", ").join(map1.keySet())));
        }
      }
    }
  }

  private static FullDataSet<ItemDescriptor> DEPENDENCY_ORDERING_TEST_DATA =
      new DataBuilder()
        .addAny(FEATURES,
              flagBuilder("a")
                  .prerequisites(prerequisite("b", 0), prerequisite("c", 0)).build(),
              flagBuilder("b")
                  .prerequisites(prerequisite("c", 0), prerequisite("e", 0)).build(),
              flagBuilder("c").build(),
              flagBuilder("d").build(),
              flagBuilder("e").build(),
              flagBuilder("f").build())
        .addAny(SEGMENTS,
              segmentBuilder("o").build())
        .build();
}
