package com.launchdarkly.sdk.server;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModelDependencies.DependencyTracker;
import com.launchdarkly.sdk.server.DataModelDependencies.KindAndKey;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.DataStoreTestTypes.TestItem;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Iterables.transform;
import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.TEST_ITEMS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toDataMap;
import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingSegment;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.prerequisite;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentRuleBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@SuppressWarnings("javadoc")
public class DataModelDependenciesTest {
  @Test
  public void computeDependenciesFromFlag() {
    FeatureFlag flag1 = flagBuilder("key").build();
    
    assertThat(
        DataModelDependencies.computeDependenciesFrom(
            DataModel.FEATURES,
            new ItemDescriptor(flag1.getVersion(), flag1)
            ),
        emptyIterable()
        );

    FeatureFlag flag2 = ModelBuilders.flagBuilder("key")
        .prerequisites(
            prerequisite("flag2", 0),
            prerequisite("flag3", 0)
            )
        .rules(
            ruleBuilder()
              .clauses(
                  clause("key", Operator.in, LDValue.of("ignore")),
                  clauseMatchingSegment("segment1", "segment2")
                  )
              .build(),
            ruleBuilder()
              .clauses(
                  clauseMatchingSegment("segment3")
                  )
              .build()
              )
        .build();
    
    assertThat(
        DataModelDependencies.computeDependenciesFrom(
            DataModel.FEATURES,
            new ItemDescriptor(flag2.getVersion(), flag2)
            ),
        contains(
            new KindAndKey(FEATURES, "flag2"),
            new KindAndKey(FEATURES, "flag3"),
            new KindAndKey(SEGMENTS, "segment1"),
            new KindAndKey(SEGMENTS, "segment2"),
            new KindAndKey(SEGMENTS, "segment3")
            )
        );
  }
  
  @Test
  public void computeDependenciesFromSegment() {
    Segment segment = segmentBuilder("segment").build();
    
    assertThat(
        DataModelDependencies.computeDependenciesFrom(
            DataModel.SEGMENTS,
            new ItemDescriptor(segment.getVersion(), segment)
            ),
        emptyIterable()
        );
  }
  
  @Test
  public void computeDependenciesFromUnknownDataKind() {
    assertThat(
        DataModelDependencies.computeDependenciesFrom(
            DataStoreTestTypes.TEST_ITEMS,
            new ItemDescriptor(1, new DataStoreTestTypes.TestItem("x", 1))
            ),
        emptyIterable()
        );
  }

  @Test
  public void computeDependenciesFromNullItem() {
    assertThat(
        DataModelDependencies.computeDependenciesFrom(
            DataModel.FEATURES,
            new ItemDescriptor(1, null)
            ),
        emptyIterable()
        );

    assertThat(
        DataModelDependencies.computeDependenciesFrom(
            DataModel.FEATURES,
            null
            ),
        emptyIterable()
        );
  }

  @Test
  public void sortAllCollections() {
    FullDataSet<ItemDescriptor> result = DataModelDependencies.sortAllCollections(DEPENDENCY_ORDERING_TEST_DATA);
    verifySortedData(result, DEPENDENCY_ORDERING_TEST_DATA);
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void sortAllCollectionsLeavesItemsOfUnknownDataKindUnchanged() {
    TestItem extraItem1 = new TestItem("item1", 1);
    TestItem extraItem2 = new TestItem("item2", 1);
    FullDataSet<ItemDescriptor> inputData = new DataBuilder()
        .addAny(FEATURES,
          flagBuilder("a")
              .prerequisites(prerequisite("b", 0), prerequisite("c", 0)).build(),
          flagBuilder("b")
              .prerequisites(prerequisite("c", 0)).build(),
          flagBuilder("c").build())
        .addAny(SEGMENTS)
        .addAny(TEST_ITEMS, extraItem1, extraItem2)
        .build();
    
    FullDataSet<ItemDescriptor> result = DataModelDependencies.sortAllCollections(inputData);
    assertThat(result.getData(), iterableWithSize(3));
    
    // the unknown data kind appears last, and the ordering of its items is unchanged
    assertThat(transform(result.getData(), coll -> coll.getKey()),
        contains(SEGMENTS, FEATURES, TEST_ITEMS));
    assertThat(Iterables.get(result.getData(), 2).getValue().getItems(),
        contains(extraItem1.toKeyedItemDescriptor(), extraItem2.toKeyedItemDescriptor()));
  }
  
  static void verifySortedData(FullDataSet<ItemDescriptor> sortedData, FullDataSet<ItemDescriptor> inputData) {
    Map<DataKind, Map<String, ItemDescriptor>> dataMap = toDataMap(sortedData);
    assertEquals(2, dataMap.size());
    Map<DataKind, Map<String, ItemDescriptor>> inputDataMap = toDataMap(inputData);
    
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
  
  @Test
  public void dependencyTrackerReturnsSingleValueResultForUnknownItem() {
    DependencyTracker dt = new DependencyTracker();
    
    // a change to any item with no known depenencies affects only itself
    verifyAffectedItems(dt, FEATURES, "flag1",
        new KindAndKey(FEATURES, "flag1"));
  }
  
  @Test
  public void dependencyTrackerBuildsGraph() {
    DependencyTracker dt = new DependencyTracker();
    
    Segment segment1 = segmentBuilder("segment1").build();
    Segment segment2 = segmentBuilder("segment2").
        rules(segmentRuleBuilder().clauses(clauseMatchingSegment("segment3")).build())
        .build();
    Segment segment3 = segmentBuilder("segment3").build();
    
    FeatureFlag flag1 = flagBuilder("flag1")
        .prerequisites(
            prerequisite("flag2", 0),
            prerequisite("flag3", 0)
            )
        .rules(
            ruleBuilder()
              .clauses(
                  clauseMatchingSegment("segment1", "segment2")
                  )
              .build()
              )
        .build();

    FeatureFlag flag2 = flagBuilder("flag2")
        .prerequisites(
            prerequisite("flag4", 0)
            )
        .rules(
            ruleBuilder()
              .clauses(
                  clauseMatchingSegment("segment2")
                  )
              .build()
              )
        .build();

    for (Segment s: new Segment[] {segment1, segment2, segment3}) {
      dt.updateDependenciesFrom(SEGMENTS, s.getKey(), new ItemDescriptor(s.getVersion(), s));
    }
    for (FeatureFlag f: new FeatureFlag[] {flag1, flag2}) {
      dt.updateDependenciesFrom(FEATURES, f.getKey(), new ItemDescriptor(f.getVersion(), f));
    }

    // a change to flag1 affects only flag1
    verifyAffectedItems(dt, FEATURES, "flag1",
        new KindAndKey(FEATURES, "flag1"));
    
    // a change to flag2 affects flag2 and flag1
    verifyAffectedItems(dt, FEATURES, "flag2",
        new KindAndKey(FEATURES, "flag2"),
        new KindAndKey(FEATURES, "flag1"));
    
    // a change to flag3 affects flag3 and flag1
    verifyAffectedItems(dt, FEATURES, "flag3",
        new KindAndKey(FEATURES, "flag3"),
        new KindAndKey(FEATURES, "flag1"));
    
    // a change to segment1 affects segment1 and flag1
    verifyAffectedItems(dt, SEGMENTS, "segment1",
        new KindAndKey(SEGMENTS, "segment1"),
        new KindAndKey(FEATURES, "flag1"));

    // a change to segment2 affects segment2, flag1, and flag2
    verifyAffectedItems(dt, SEGMENTS, "segment2",
        new KindAndKey(SEGMENTS, "segment2"),
        new KindAndKey(FEATURES, "flag1"),
        new KindAndKey(FEATURES, "flag2"));

    // a change to segment3 affects segment3, segment2, flag1, and flag2
    verifyAffectedItems(dt, SEGMENTS, "segment3",
        new KindAndKey(SEGMENTS, "segment3"),
        new KindAndKey(SEGMENTS, "segment2"),
        new KindAndKey(FEATURES, "flag1"),
        new KindAndKey(FEATURES, "flag2"));
  }

  @Test
  public void dependencyTrackerUpdatesGraph() {
    DependencyTracker dt = new DependencyTracker();
    
    FeatureFlag flag1 = ModelBuilders.flagBuilder("flag1")
        .prerequisites(prerequisite("flag3", 0))
        .build();
    dt.updateDependenciesFrom(FEATURES, flag1.getKey(), new ItemDescriptor(flag1.getVersion(), flag1));

    FeatureFlag flag2 = ModelBuilders.flagBuilder("flag2")
        .prerequisites(prerequisite("flag3", 0))
        .build();
    dt.updateDependenciesFrom(FEATURES, flag2.getKey(), new ItemDescriptor(flag2.getVersion(), flag2));

    // at this point, a change to flag3 affects flag3, flag2, and flag1
    verifyAffectedItems(dt, FEATURES, "flag3",
        new KindAndKey(FEATURES, "flag3"),
        new KindAndKey(FEATURES, "flag2"),
        new KindAndKey(FEATURES, "flag1"));
    
    // now make it so flag1 now depends on flag4 instead of flag2
    FeatureFlag flag1v2 = ModelBuilders.flagBuilder("flag1")
        .prerequisites(prerequisite("flag4", 0))
        .build();
    dt.updateDependenciesFrom(FEATURES, flag1.getKey(), new ItemDescriptor(flag1v2.getVersion(), flag1v2));

    // now, a change to flag3 affects flag3 and flag2
    verifyAffectedItems(dt, FEATURES, "flag3",
        new KindAndKey(FEATURES, "flag3"),
        new KindAndKey(FEATURES, "flag2"));

    // and a change to flag4 affects flag4 and flag1
    verifyAffectedItems(dt, FEATURES, "flag4",
        new KindAndKey(FEATURES, "flag4"),
        new KindAndKey(FEATURES, "flag1"));
  }

  @Test
  public void dependencyTrackerResetsGraph() {
    DependencyTracker dt = new DependencyTracker();
    
    FeatureFlag flag1 = ModelBuilders.flagBuilder("flag1")
        .prerequisites(prerequisite("flag3", 0))
        .build();
    dt.updateDependenciesFrom(FEATURES, flag1.getKey(), new ItemDescriptor(flag1.getVersion(), flag1));

    verifyAffectedItems(dt, FEATURES, "flag3",
        new KindAndKey(FEATURES, "flag3"),
        new KindAndKey(FEATURES, "flag1"));

    dt.reset();

    verifyAffectedItems(dt, FEATURES, "flag3",
        new KindAndKey(FEATURES, "flag3"));
  }
  
  private void verifyAffectedItems(DependencyTracker dt, DataKind kind, String key, KindAndKey... expected) {
    Set<KindAndKey> result = new HashSet<>();
    dt.addAffectedItems(result, new KindAndKey(kind, key));
    assertThat(result, equalTo(ImmutableSet.copyOf(expected)));
  }
  
  @Test
  public void kindAndKeyEquality() {
    KindAndKey kk1 = new KindAndKey(FEATURES, "key1");
    KindAndKey kk2 = new KindAndKey(FEATURES, "key1");
    assertThat(kk1, equalTo(kk2));
    assertThat(kk2, equalTo(kk1));
    assertThat(kk1.hashCode(), equalTo(kk2.hashCode()));
    
    KindAndKey kk3 = new KindAndKey(FEATURES, "key2");
    assertThat(kk3, not(equalTo(kk1)));
    assertThat(kk1, not(equalTo(kk3)));
    
    KindAndKey kk4 = new KindAndKey(SEGMENTS, "key1");
    assertThat(kk4, not(equalTo(kk1)));
    assertThat(kk1, not(equalTo(kk4)));
    
    assertThat(kk1, not(equalTo(null)));
    assertThat(kk1, not(equalTo("x")));
  }
  
  static FullDataSet<ItemDescriptor> DEPENDENCY_ORDERING_TEST_DATA =
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
