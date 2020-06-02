package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;

import org.junit.Test;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

import static com.google.common.collect.Iterables.get;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

@SuppressWarnings("javadoc")
public class TestDataTest {
  private static final LDValue[] THREE_STRING_VALUES =
      new LDValue[] { LDValue.of("red"), LDValue.of("green"), LDValue.of("blue") };
  
  private CapturingDataSourceUpdates updates = new CapturingDataSourceUpdates();
  
  @Test
  public void initializesWithEmptyData() throws Exception {
    TestData td = TestData.dataSource();
    DataSource ds = td.createDataSource(null, updates);
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
    
    DataSource ds = td.createDataSource(null, updates);
    Future<Void> started = ds.start();
    
    assertThat(started.isDone(), is(true));
    assertThat(updates.valid, is(true));

    assertThat(updates.inits.size(), equalTo(1));
    FullDataSet<ItemDescriptor> data = updates.inits.take();
    assertThat(data.getData(), iterableWithSize(1));
    assertThat(get(data.getData(), 0).getKey(), equalTo(DataModel.FEATURES));
    assertThat(get(data.getData(), 0).getValue().getItems(), iterableWithSize(2));
    
    Map<String, ItemDescriptor> flags = ImmutableMap.copyOf(get(data.getData(), 0).getValue().getItems());
    ItemDescriptor flag1 = flags.get("flag1"); 
    ItemDescriptor flag2 = flags.get("flag2");
    assertThat(flag1, not(nullValue()));
    assertThat(flag2, not(nullValue()));
    assertThat(flag1.getVersion(), equalTo(1));
    assertThat(flag2.getVersion(), equalTo(1));
    assertThat(flagJson(flag1).get("on").booleanValue(), is(true));
    assertThat(flagJson(flag2).get("on").booleanValue(), is(false));
  }
  
  @Test
  public void addsFlag() throws Exception {
    TestData td = TestData.dataSource();
    DataSource ds = td.createDataSource(null, updates);
    Future<Void> started = ds.start();
    
    assertThat(started.isDone(), is(true));
    assertThat(updates.valid, is(true));

    td.update(td.flag("flag1").on(true));
    
    assertThat(updates.upserts.size(), equalTo(1));
    UpsertParams up = updates.upserts.take();
    assertThat(up.kind, is(DataModel.FEATURES));
    assertThat(up.key, equalTo("flag1"));
    ItemDescriptor flag1 = up.item;
    assertThat(flag1.getVersion(), equalTo(1));
    assertThat(flagJson(flag1).get("on").booleanValue(), is(true));
  }

  @Test
  public void updatesFlag() throws Exception {
    TestData td = TestData.dataSource();
    td.update(td.flag("flag1").on(false));
    
    DataSource ds = td.createDataSource(null, updates);
    Future<Void> started = ds.start();
    
    assertThat(started.isDone(), is(true));
    assertThat(updates.valid, is(true));

    td.update(td.flag("flag1").on(true));
    
    assertThat(updates.upserts.size(), equalTo(1));
    UpsertParams up = updates.upserts.take();
    assertThat(up.kind, is(DataModel.FEATURES));
    assertThat(up.key, equalTo("flag1"));
    ItemDescriptor flag1 = up.item;
    assertThat(flag1.getVersion(), equalTo(2));
    assertThat(flagJson(flag1).get("on").booleanValue(), is(true));
  }
  
  @Test
  public void flagConfigSimpleBoolean() throws Exception {
    String basicProps = "\"key\":\"flag\",\"version\":1,\"variations\":[true,false]" +
        ",\"offVariation\":1,\"fallthrough\":{\"variation\":0}";
    String onProps = basicProps + ",\"on\":true";
    String offProps = basicProps + ",\"on\":false";

    verifyFlag(td -> td.flag("flag"), onProps);
    verifyFlag(td -> td.flag("flag").booleanFlag(), onProps);
    verifyFlag(td -> td.flag("flag").on(true), onProps);
    verifyFlag(td -> td.flag("flag").on(false), offProps);
    verifyFlag(td -> td.flag("flag").variationForAllUsers(false), offProps);
    verifyFlag(td -> td.flag("flag").variationForAllUsers(true), onProps);

    verifyFlag(
        td -> td.flag("flag").fallthroughVariation(true).offVariation(false),
        onProps
        );

    verifyFlag(
        td -> td.flag("flag").fallthroughVariation(false).offVariation(true),
        "\"key\":\"flag\",\"version\":1,\"variations\":[true,false],\"on\":true" +
            ",\"offVariation\":0,\"fallthrough\":{\"variation\":1}"
        );
  }

  @Test
  public void usingBooleanConfigMethodsForcesFlagToBeBoolean() throws Exception {
    String booleanProps = "\"key\":\"flag\",\"version\":1,\"on\":true"
        + ",\"variations\":[true,false],\"offVariation\":1,\"fallthrough\":{\"variation\":0}";

    verifyFlag(
        td -> td.flag("flag")
          .variations(LDValue.of(1), LDValue.of(2))
          .booleanFlag(),
        booleanProps
        );
    verifyFlag(
        td -> td.flag("flag")
          .variations(LDValue.of(true), LDValue.of(2))
          .booleanFlag(),
          booleanProps
        );
    verifyFlag(
        td -> td.flag("flag").valueForAllUsers(LDValue.of("x"))
          .booleanFlag(),
          booleanProps
        );
  }
  
  @Test
  public void flagConfigStringVariations() throws Exception {
    String basicProps = "\"key\":\"flag\",\"version\":1,\"variations\":[\"red\",\"green\",\"blue\"],\"on\":true"
        + ",\"offVariation\":0,\"fallthrough\":{\"variation\":2}";

    verifyFlag(
        td -> td.flag("flag").variations(THREE_STRING_VALUES).offVariation(0).fallthroughVariation(2),
        basicProps
        );
  }

  @Test
  public void userTargets() throws Exception {
    String booleanFlagBasicProps = "\"key\":\"flag\",\"version\":1,\"on\":true,\"variations\":[true,false]" +
        ",\"offVariation\":1,\"fallthrough\":{\"variation\":0}";
    verifyFlag(
        td -> td.flag("flag").variationForUser("a", true).variationForUser("b", true),
        booleanFlagBasicProps + ",\"targets\":[{\"variation\":0,\"values\":[\"a\",\"b\"]}]"
        );
    verifyFlag(
        td -> td.flag("flag").variationForUser("a", true).variationForUser("a", true),
        booleanFlagBasicProps + ",\"targets\":[{\"variation\":0,\"values\":[\"a\"]}]"
        );
    verifyFlag(
        td -> td.flag("flag").variationForUser("a", false).variationForUser("b", true).variationForUser("c", false),
        booleanFlagBasicProps + ",\"targets\":[{\"variation\":0,\"values\":[\"b\"]}" +
          ",{\"variation\":1,\"values\":[\"a\",\"c\"]}]"
        );
    verifyFlag(
        td -> td.flag("flag").variationForUser("a", true).variationForUser("b", true).variationForUser("a", false),
        booleanFlagBasicProps + ",\"targets\":[{\"variation\":0,\"values\":[\"b\"]}" +
          ",{\"variation\":1,\"values\":[\"a\"]}]"
        );

    String stringFlagBasicProps = "\"key\":\"flag\",\"version\":1,\"variations\":[\"red\",\"green\",\"blue\"],\"on\":true"
        + ",\"offVariation\":0,\"fallthrough\":{\"variation\":2}";
    verifyFlag(
        td -> td.flag("flag").variations(THREE_STRING_VALUES).offVariation(0).fallthroughVariation(2)
          .variationForUser("a", 2).variationForUser("b", 2),
        stringFlagBasicProps + ",\"targets\":[{\"variation\":2,\"values\":[\"a\",\"b\"]}]"
        );
    verifyFlag(
        td -> td.flag("flag").variations(THREE_STRING_VALUES).offVariation(0).fallthroughVariation(2)
          .variationForUser("a", 2).variationForUser("b", 1).variationForUser("c", 2),
          stringFlagBasicProps + ",\"targets\":[{\"variation\":1,\"values\":[\"b\"]}" +
            ",{\"variation\":2,\"values\":[\"a\",\"c\"]}]"
        );
  }
  
  @Test
  public void flagRules() throws Exception {
    String basicProps = "\"key\":\"flag\",\"version\":1,\"variations\":[true,false]" +
        ",\"on\":true,\"offVariation\":1,\"fallthrough\":{\"variation\":0}";

    // match that returns variation 0/true
    String matchReturnsVariation0 = basicProps +
        ",\"rules\":[{\"id\":\"rule0\",\"variation\":0,\"trackEvents\":false,\"clauses\":[" +
        "{\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Lucy\"],\"negate\":false}" +
        "]}]";
    verifyFlag(
        td -> td.flag("flag").ifMatch(UserAttribute.NAME, LDValue.of("Lucy")).thenReturn(true),
        matchReturnsVariation0
        );
    verifyFlag(
        td -> td.flag("flag").ifMatch(UserAttribute.NAME, LDValue.of("Lucy")).thenReturn(0),
        matchReturnsVariation0
        );

    // match that returns variation 1/false
    String matchReturnsVariation1 = basicProps +
        ",\"rules\":[{\"id\":\"rule0\",\"variation\":1,\"trackEvents\":false,\"clauses\":[" +
        "{\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Lucy\"],\"negate\":false}" +
        "]}]";    
    verifyFlag(
        td -> td.flag("flag").ifMatch(UserAttribute.NAME, LDValue.of("Lucy")).thenReturn(false),
        matchReturnsVariation1
        );
    verifyFlag(
        td -> td.flag("flag").ifMatch(UserAttribute.NAME, LDValue.of("Lucy")).thenReturn(1),
        matchReturnsVariation1
        );

    // negated match
    verifyFlag(
        td -> td.flag("flag").ifNotMatch(UserAttribute.NAME, LDValue.of("Lucy")).thenReturn(true),
        basicProps + ",\"rules\":[{\"id\":\"rule0\",\"variation\":0,\"trackEvents\":false,\"clauses\":[" +
            "{\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Lucy\"],\"negate\":true}" +
            "]}]"
        );
    
    // multiple clauses
    verifyFlag(
        td -> td.flag("flag")
          .ifMatch(UserAttribute.NAME, LDValue.of("Lucy"))
          .andMatch(UserAttribute.COUNTRY, LDValue.of("gb"))
          .thenReturn(true),
        basicProps + ",\"rules\":[{\"id\":\"rule0\",\"variation\":0,\"trackEvents\":false,\"clauses\":[" +
            "{\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Lucy\"],\"negate\":false}," +
            "{\"attribute\":\"country\",\"op\":\"in\",\"values\":[\"gb\"],\"negate\":false}" +
            "]}]"
        );

    // multiple rules
    verifyFlag(
        td -> td.flag("flag")
          .ifMatch(UserAttribute.NAME, LDValue.of("Lucy")).thenReturn(true)
          .ifMatch(UserAttribute.NAME, LDValue.of("Mina")).thenReturn(true),
        basicProps + ",\"rules\":["
          + "{\"id\":\"rule0\",\"variation\":0,\"trackEvents\":false,\"clauses\":[" +
            "{\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Lucy\"],\"negate\":false}" +
            "]},"
          + "{\"id\":\"rule1\",\"variation\":0,\"trackEvents\":false,\"clauses\":[" +
            "{\"attribute\":\"name\",\"op\":\"in\",\"values\":[\"Mina\"],\"negate\":false}" +
            "]}"
          + "]"
        );

  }
  
  private void verifyFlag(Function<TestData, TestData.FlagBuilder> makeFlag, String expectedProps) throws Exception {
    String expectedJson = "{" + expectedProps +
          ",\"clientSide\":false,\"deleted\":false,\"trackEvents\":false,\"trackEventsFallthrough\":false}";
    
    TestData td = TestData.dataSource();
    
    DataSource ds = td.createDataSource(null, updates);
    ds.start();

    td.update(makeFlag.apply(td));
    
    assertThat(updates.upserts.size(), equalTo(1));
    UpsertParams up = updates.upserts.take();
    ItemDescriptor flag = up.item;
    assertThat(flagJson(flag), equalTo(LDValue.parse(expectedJson)));    
  }
  
  private static LDValue flagJson(ItemDescriptor flag) {
    return LDValue.parse(DataModel.FEATURES.serialize(flag));
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
  
  private static class CapturingDataSourceUpdates implements DataSourceUpdates {
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
