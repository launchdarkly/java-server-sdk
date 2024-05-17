package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.ModelBuilders;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataSourceUpdateSink;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;

import org.junit.Test;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

import static com.google.common.collect.Iterables.get;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.testhelpers.JsonAssertions.assertJsonEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@SuppressWarnings("javadoc")
public class TestDataTest {
  private static final LDValue[] THREE_STRING_VALUES =
      new LDValue[] { LDValue.of("red"), LDValue.of("green"), LDValue.of("blue") };
  
  private CapturingDataSourceUpdates updates = new CapturingDataSourceUpdates();
  
  // Test implementation note: We're using the ModelBuilders test helpers to build the expected
  // flag JSON. However, we have to use them in a slightly different way than we do in other tests
  // (for instance, writing out an expected clause as a JSON literal), because specific data model
  // classes like FeatureFlag and Clause aren't visible from the integrations package. 
  
  @Test
  public void initializesWithEmptyData() throws Exception {
    TestData td = TestData.dataSource();
    DataSource ds = td.build(clientContext("", new LDConfig.Builder().build(), updates));
    Future<Void> started = ds.start();
    
    assertThat(started.isDone(), is(true));
    assertThat(updates.valid, is(true));

    assertThat(updates.inits.size(), equalTo(1));
    FullDataSet<ItemDescriptor> data = updates.inits.take();
    assertThat(data.getData(), iterableWithSize(1));
    assertThat(get(data.getData(), 0).getKey(), equalTo(DataModel.FEATURES));
    assertThat(get(data.getData(), 0).getValue().getItems(), emptyIterable());
  }

  @Test
  public void initializesWithFlags() throws Exception {
    TestData td = TestData.dataSource();
    
    td.update(td.flag("flag1").on(true))
      .update(td.flag("flag2").on(false));
    
    DataSource ds = td.build(clientContext("", new LDConfig.Builder().build(), updates));
    Future<Void> started = ds.start();
    
    assertThat(started.isDone(), is(true));
    assertThat(updates.valid, is(true));

    assertThat(updates.inits.size(), equalTo(1));
    FullDataSet<ItemDescriptor> data = updates.inits.take();
    assertThat(data.getData(), iterableWithSize(1));
    assertThat(get(data.getData(), 0).getKey(), equalTo(DataModel.FEATURES));
    assertThat(get(data.getData(), 0).getValue().getItems(), iterableWithSize(2));
    
    ModelBuilders.FlagBuilder expectedFlag1 = flagBuilder("flag1").version(1).salt("")
        .on(true).offVariation(1).fallthroughVariation(0).variations(true, false);
    ModelBuilders.FlagBuilder expectedFlag2 = flagBuilder("flag2").version(1).salt("")
        .on(false).offVariation(1).fallthroughVariation(0).variations(true, false);

    Map<String, ItemDescriptor> flags = ImmutableMap.copyOf(get(data.getData(), 0).getValue().getItems());
    ItemDescriptor flag1 = flags.get("flag1"); 
    ItemDescriptor flag2 = flags.get("flag2");
    assertThat(flag1, not(nullValue()));
    assertThat(flag2, not(nullValue()));
    
    assertJsonEquals(flagJson(expectedFlag1, 1), flagJson(flag1));
    assertJsonEquals(flagJson(expectedFlag2, 1), flagJson(flag2));
  }
  
  @Test
  public void addsFlag() throws Exception {
    TestData td = TestData.dataSource();
    DataSource ds = td.build(clientContext("", new LDConfig.Builder().build(), updates));
    Future<Void> started = ds.start();
    
    assertThat(started.isDone(), is(true));
    assertThat(updates.valid, is(true));

    td.update(td.flag("flag1").on(true));
    
    ModelBuilders.FlagBuilder expectedFlag = flagBuilder("flag1").version(1).salt("")
        .on(true).offVariation(1).fallthroughVariation(0).variations(true, false);

    assertThat(updates.upserts.size(), equalTo(1));
    UpsertParams up = updates.upserts.take();
    assertThat(up.kind, is(DataModel.FEATURES));
    assertThat(up.key, equalTo("flag1"));
    ItemDescriptor flag1 = up.item;
    
    assertJsonEquals(flagJson(expectedFlag, 2), flagJson(flag1));
  }

  @Test
  public void updatesFlag() throws Exception {
    TestData td = TestData.dataSource();
    td.update(td.flag("flag1")
      .on(false)
      .variationForUser("a", true)
      .ifMatch("name", LDValue.of("Lucy")).thenReturn(true));
      // Here we're verifying that the original targets & rules are copied over if we didn't change them

    ModelBuilders.FlagBuilder expectedFlag = flagBuilder("flag1").version(1).salt("")
        .on(false).offVariation(1).fallthroughVariation(0).variations(true, false)
        .addTarget(0, "a").addContextTarget(ContextKind.DEFAULT, 0)
        .addRule("rule0", 0, "{\"contextKind\":\"user\",\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Lucy\"]}");

    DataSource ds = td.build(clientContext("", new LDConfig.Builder().build(), updates));
    Future<Void> started = ds.start();
    
    assertThat(started.isDone(), is(true));
    assertThat(updates.valid, is(true));

    td.update(td.flag("flag1").on(true));
    
    assertThat(updates.upserts.size(), equalTo(1));
    UpsertParams up = updates.upserts.take();
    assertThat(up.kind, is(DataModel.FEATURES));
    assertThat(up.key, equalTo("flag1"));
    ItemDescriptor flag1 = up.item;
    
    expectedFlag.on(true).version(2);
    assertJsonEquals(flagJson(expectedFlag, 2), flagJson(flag1));
  }

  @Test
  public void deletesFlag() throws Exception {
    final TestData td = TestData.dataSource();

    try (final DataSource ds = td.build(clientContext("", new LDConfig.Builder().build(), updates))) {
      final Future<Void> started = ds.start();
      assertThat(started.isDone(), is(true));
      assertThat(updates.valid, is(true));

      td.update(td.flag("foo").on(false).valueForAll(LDValue.of("bar")));
      td.delete("foo");

      assertThat(updates.upserts.size(), equalTo(2));
      UpsertParams up = updates.upserts.take();
      assertThat(up.kind, is(DataModel.FEATURES));
      assertThat(up.key, equalTo("foo"));
      assertThat(up.item.getVersion(), equalTo(1));
      assertThat(up.item.getItem(), notNullValue());

      up = updates.upserts.take();
      assertThat(up.kind, is(DataModel.FEATURES));
      assertThat(up.key, equalTo("foo"));
      assertThat(up.item.getVersion(), equalTo(2));
      assertThat(up.item.getItem(), nullValue());
    }
  }

  @Test
  public void flagConfigSimpleBoolean() throws Exception {
    Function<ModelBuilders.FlagBuilder, ModelBuilders.FlagBuilder> expectedBooleanFlag = fb ->
      fb.on(true).variations(true, false).offVariation(1).fallthroughVariation(0);

    verifyFlag(f -> f, expectedBooleanFlag);
    verifyFlag(f -> f.booleanFlag(), expectedBooleanFlag); // already the default
    verifyFlag(f -> f.on(true), expectedBooleanFlag);      // already the default
    verifyFlag(f -> f.on(false), fb -> expectedBooleanFlag.apply(fb).on(false));
    verifyFlag(f -> f.variationForAll(false), fb -> expectedBooleanFlag.apply(fb).fallthroughVariation(1));
    verifyFlag(f -> f.variationForAll(true), expectedBooleanFlag); // already the default
    verifyFlag(f -> f.fallthroughVariation(true).offVariation(false), expectedBooleanFlag); // already the default
    
    verifyFlag(
        f -> f.fallthroughVariation(false).offVariation(true),
        fb -> expectedBooleanFlag.apply(fb).fallthroughVariation(1).offVariation(0)
        );
  }

  @Test
  public void usingBooleanConfigMethodsForcesFlagToBeBoolean() throws Exception {
    Function<ModelBuilders.FlagBuilder, ModelBuilders.FlagBuilder> expectedBooleanFlag = fb ->
      fb.on(true).variations(true, false).offVariation(1).fallthroughVariation(0);

      verifyFlag(
          f -> f.variations(LDValue.of(1), LDValue.of(2)).booleanFlag(),
          expectedBooleanFlag
          );
      verifyFlag(
          f -> f.variations(LDValue.of(true), LDValue.of(2)).booleanFlag(),
          expectedBooleanFlag
          );
      verifyFlag(
          f -> f.booleanFlag(),
          expectedBooleanFlag
          );
  }
  
  @Test
  public void flagConfigStringVariations() throws Exception {
    verifyFlag(
        f -> f.variations(THREE_STRING_VALUES).offVariation(0).fallthroughVariation(2),
        fb -> fb.variations("red", "green", "blue").on(true).offVariation(0).fallthroughVariation(2)
        );
  }

  @Test
  public void flagConfigSamplingRatio() throws Exception {
    verifyFlag(
        f -> f.samplingRatio(2).on(false),
        fb -> fb.samplingRatio(2).fallthroughVariation(0).variations(true,false).offVariation(1)
    );
  }

  @Test
  public void flagConfigMigrationCheckRatio() throws Exception {
    verifyFlag(
        f -> f.migrationCheckRatio(2).on(false),
        fb -> fb.migration(new ModelBuilders.MigrationBuilder().checkRatio(2).build())
            .fallthroughVariation(0).variations(true,false).offVariation(1)
    );
  }

  @Test
  public void userTargets() throws Exception {
    Function<ModelBuilders.FlagBuilder, ModelBuilders.FlagBuilder> expectedBooleanFlag = fb ->
        fb.variations(true, false).on(true).offVariation(1).fallthroughVariation(0);

    verifyFlag(
        f -> f.variationForUser("a", true).variationForUser("b", true),
        fb -> expectedBooleanFlag.apply(fb).addTarget(0, "a", "b")
          .addContextTarget(ContextKind.DEFAULT, 0)
        );
    verifyFlag(
        f -> f.variationForUser("a", true).variationForUser("a", true),
        fb -> expectedBooleanFlag.apply(fb).addTarget(0, "a")
          .addContextTarget(ContextKind.DEFAULT, 0)
        );
    verifyFlag(
        f -> f.variationForUser("a", true).variationForUser("a", false),
        fb -> expectedBooleanFlag.apply(fb).addTarget(1, "a")
          .addContextTarget(ContextKind.DEFAULT, 1)
        );
    verifyFlag(
        f -> f.variationForUser("a", false).variationForUser("b", true).variationForUser("c", false),
        fb -> expectedBooleanFlag.apply(fb).addTarget(0, "b").addTarget(1, "a", "c")
          .addContextTarget(ContextKind.DEFAULT, 0).addContextTarget(ContextKind.DEFAULT, 1)
        );
    verifyFlag(
        f -> f.variationForUser("a", true).variationForUser("b", true).variationForUser("a", false),
        fb -> expectedBooleanFlag.apply(fb).addTarget(0, "b").addTarget(1, "a")
          .addContextTarget(ContextKind.DEFAULT, 0).addContextTarget(ContextKind.DEFAULT, 1)
        );
    
    Function<ModelBuilders.FlagBuilder, ModelBuilders.FlagBuilder> expectedStringFlag = fb ->
        fb.variations("red", "green", "blue").on(true).offVariation(0).fallthroughVariation(2);
    
    verifyFlag(
        f -> f.variations(THREE_STRING_VALUES).offVariation(0).fallthroughVariation(2)
          .variationForUser("a", 2).variationForUser("b", 2),
        fb -> expectedStringFlag.apply(fb).addTarget(2, "a", "b")
          .addContextTarget(ContextKind.DEFAULT, 2)
        );
    verifyFlag(
        f -> f.variations(THREE_STRING_VALUES).offVariation(0).fallthroughVariation(2)
          .variationForUser("a", 2).variationForUser("b", 1).variationForUser("c", 2),
        fb -> expectedStringFlag.apply(fb).addTarget(1, "b").addTarget(2, "a", "c")
          .addContextTarget(ContextKind.DEFAULT, 1).addContextTarget(ContextKind.DEFAULT, 2)
        );
    
    // clear previously set targets
    verifyFlag(
        f -> f.variationForUser("a", true).clearTargets(),
        expectedBooleanFlag
        );
  }
  
  @Test
  public void contextTargets() throws Exception {
    ContextKind kind1 = ContextKind.of("org"), kind2 = ContextKind.of("other");

    Function<ModelBuilders.FlagBuilder, ModelBuilders.FlagBuilder> expectedBooleanFlag = fb ->
        fb.variations(true, false).on(true).offVariation(1).fallthroughVariation(0);

    verifyFlag(
        f -> f.variationForKey(kind1, "a", true).variationForKey(kind1, "b", true),
        fb -> expectedBooleanFlag.apply(fb).addContextTarget(kind1, 0, "a", "b")
        );
    verifyFlag(
        f -> f.variationForKey(kind1, "a", true).variationForKey(kind2, "a", true),
        fb -> expectedBooleanFlag.apply(fb).addContextTarget(kind1, 0, "a").addContextTarget(kind2, 0, "a")
        );
    verifyFlag(
        f -> f.variationForKey(kind1, "a", true).variationForKey(kind1, "a", true),
        fb -> expectedBooleanFlag.apply(fb).addContextTarget(kind1, 0, "a")
        );
    verifyFlag(
        f -> f.variationForKey(kind1, "a", true).variationForKey(kind1, "a", false),
        fb -> expectedBooleanFlag.apply(fb).addContextTarget(kind1, 1, "a")
        );

    Function<ModelBuilders.FlagBuilder, ModelBuilders.FlagBuilder> expectedStringFlag = fb ->
        fb.variations("red", "green", "blue").on(true).offVariation(0).fallthroughVariation(2);

    verifyFlag(
        f -> f.variations(THREE_STRING_VALUES).offVariation(0).fallthroughVariation(2)
          .variationForKey(kind1, "a", 2).variationForKey(kind1, "b", 2),
        fb -> expectedStringFlag.apply(fb).addContextTarget(kind1, 2, "a", "b")
        );
    
    // clear previously set targets
    verifyFlag(
        f -> f.variationForKey(kind1, "a", true).clearTargets(),
        expectedBooleanFlag
        );
  }
  
  @Test
  public void flagRules() throws Exception {
    Function<ModelBuilders.FlagBuilder, ModelBuilders.FlagBuilder> expectedBooleanFlag = fb ->
      fb.variations(true, false).on(true).offVariation(1).fallthroughVariation(0);

    // match that returns variation 0/true
    Function<ModelBuilders.FlagBuilder, ModelBuilders.FlagBuilder> matchReturnsVariation0 = fb ->
        expectedBooleanFlag.apply(fb).addRule("rule0", 0, 
            "{\"contextKind\":\"user\",\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Lucy\"]}");
        
    verifyFlag(
        f -> f.ifMatch("name", LDValue.of("Lucy")).thenReturn(true),
        matchReturnsVariation0
        );
    verifyFlag(
        f -> f.ifMatch("name", LDValue.of("Lucy")).thenReturn(0),
        matchReturnsVariation0
        );
    
    // match that returns variation 1/false
    Function<ModelBuilders.FlagBuilder, ModelBuilders.FlagBuilder> matchReturnsVariation1 = fb ->
        expectedBooleanFlag.apply(fb).addRule("rule0", 1, 
            "{\"contextKind\":\"user\",\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Lucy\"]}");
   
    verifyFlag(
        f -> f.ifMatch("name", LDValue.of("Lucy")).thenReturn(false),
        matchReturnsVariation1
        );
    verifyFlag(
        f -> f.ifMatch("name", LDValue.of("Lucy")).thenReturn(1),
        matchReturnsVariation1
        );
    
    // negated match
    verifyFlag(
        f -> f.ifNotMatch("name", LDValue.of("Lucy")).thenReturn(true),
        fb -> expectedBooleanFlag.apply(fb).addRule("rule0", 0, 
            "{\"contextKind\":\"user\",\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Lucy\"],\"negate\":true}")
        );

    // context kinds
    verifyFlag(
        f -> f.ifMatch(ContextKind.of("org"), "name", LDValue.of("Catco")).thenReturn(true),
        fb -> expectedBooleanFlag.apply(fb).addRule("rule0", 0, 
            "{\"contextKind\":\"org\",\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Catco\"]}")
        );
    verifyFlag(
        f -> f.ifNotMatch(ContextKind.of("org"), "name", LDValue.of("Catco")).thenReturn(true),
        fb -> expectedBooleanFlag.apply(fb).addRule("rule0", 0, 
            "{\"contextKind\":\"org\",\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Catco\"],\"negate\":true}")
        );
    
    // multiple clauses
    verifyFlag(
        f -> f.ifMatch("name", LDValue.of("Lucy"))
          .andMatch("country", LDValue.of("gb"))
          .thenReturn(true),
        fb -> expectedBooleanFlag.apply(fb).addRule("rule0", 0, 
            "{\"contextKind\":\"user\",\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Lucy\"]}",
            "{\"contextKind\":\"user\",\"attribute\":\"country\",\"op\":\"in\",\"values\":[\"gb\"]}")
        );
    verifyFlag(
        f -> f.ifMatch("name", LDValue.of("Lucy"))
          .andMatch("country", LDValue.of("gb"))
          .thenReturn(true),
        fb -> expectedBooleanFlag.apply(fb).addRule("rule0", 0, 
            "{\"contextKind\":\"user\",\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Lucy\"]}",
            "{\"contextKind\":\"user\",\"attribute\":\"country\",\"op\":\"in\",\"values\":[\"gb\"]}")
        );
    
    // multiple rules
    verifyFlag(
        f -> f.ifMatch("name", LDValue.of("Lucy")).thenReturn(true)
          .ifMatch("name", LDValue.of("Mina")).thenReturn(false),
        fb -> expectedBooleanFlag.apply(fb)
          .addRule("rule0", 0, "{\"contextKind\":\"user\",\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Lucy\"]}")
          .addRule("rule1", 1, "{\"contextKind\":\"user\",\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Mina\"]}")
        );
    
    // clear previously set rules
    verifyFlag(
        f -> f.ifMatch("name", LDValue.of("Lucy")).thenReturn(true).clearRules(),
        expectedBooleanFlag
        );
  }

  private void verifyFlag(
      Function<TestData.FlagBuilder, TestData.FlagBuilder> configureFlag,
      Function<ModelBuilders.FlagBuilder, ModelBuilders.FlagBuilder> configureExpectedFlag
      ) throws Exception {
    ModelBuilders.FlagBuilder expectedFlag = flagBuilder("flagkey").version(1).salt("");
    expectedFlag = configureExpectedFlag.apply(expectedFlag);

    TestData td = TestData.dataSource();
    
    DataSource ds = td.build(clientContext("", new LDConfig.Builder().build(), updates));
    ds.start();
    
    td.update(configureFlag.apply(td.flag("flagkey")));
    
    assertThat(updates.upserts.size(), equalTo(1));
    UpsertParams up = updates.upserts.take();
    ItemDescriptor flag = up.item;
    assertJsonEquals(flagJson(expectedFlag, 1), flagJson(flag));
  }

  private static String flagJson(ModelBuilders.FlagBuilder flagBuilder, int version) {
    return DataModel.FEATURES.serialize(new ItemDescriptor(version, flagBuilder.build()));
  }
  
  private static String flagJson(ItemDescriptor flag) {
    return DataModel.FEATURES.serialize(flag);
  }
  
  private static class UpsertParams {
    final DataKind kind;
    final String key;
    final ItemDescriptor item;
    
    UpsertParams(DataKind kind, String key, ItemDescriptor item) {
      this.kind = kind;
      this.key = key;
      this.item = item;
    }
  }
  
  private static class CapturingDataSourceUpdates implements DataSourceUpdateSink {
    BlockingQueue<FullDataSet<ItemDescriptor>> inits = new LinkedBlockingQueue<>();
    BlockingQueue<UpsertParams> upserts = new LinkedBlockingQueue<>();
    boolean valid;
    
    @Override
    public boolean init(FullDataSet<ItemDescriptor> allData) {
      inits.add(allData);
      return true;
    }

    @Override
    public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
      upserts.add(new UpsertParams(kind, key, item));
      return true;
    }

    @Override
    public DataStoreStatusProvider getDataStoreStatusProvider() {
      return null;
    }

    @Override
    public void updateStatus(State newState, ErrorInfo newError) {
      valid = newState == State.VALID;
    }
  }
}
