package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.launchdarkly.client.EventSummarizer.CounterData;

import org.junit.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventSummarizerTest {
  private static final LDConfig defaultConfig = new LDConfig.Builder().userKeysCapacity(100).build();
  private static final LDUser user = new LDUser.Builder("key").build();
  
  private long eventTimestamp;
  private EventFactory eventFactory = new EventFactory() {
    @Override
    protected long getTimestamp() {
      return eventTimestamp;
    }
  };
  
  @Test
  public void noticeUserReturnsFalseForNeverSeenUser() {
    EventSummarizer es = new EventSummarizer(defaultConfig);
    assertFalse(es.noticeUser(user));
  }
  
  @Test
  public void noticeUserReturnsTrueForPreviouslySeenUser() {
    EventSummarizer es = new EventSummarizer(defaultConfig);
    es.noticeUser(user);
    LDUser user2 = new LDUser.Builder(user).build();
    assertTrue(es.noticeUser(user2));
  }
  
  @Test
  public void oldestUserForgottenIfCapacityExceeded() {
    LDConfig config = new LDConfig.Builder().userKeysCapacity(2).build();
    EventSummarizer es = new EventSummarizer(config);
    LDUser user1 = new LDUser.Builder("key1").build();
    LDUser user2 = new LDUser.Builder("key2").build();
    LDUser user3 = new LDUser.Builder("key3").build();
    es.noticeUser(user1);
    es.noticeUser(user2);
    es.noticeUser(user3);
    assertTrue(es.noticeUser(user3));
    assertTrue(es.noticeUser(user2));
    assertFalse(es.noticeUser(user1));
  }
  
  @Test
  public void summarizeEventDoesNothingForIdentifyEvent() {
    EventSummarizer es = new EventSummarizer(defaultConfig);
    EventSummarizer.SummaryState snapshot = es.snapshot();
    es.summarizeEvent(eventFactory.newIdentifyEvent(user));
    
    assertEquals(snapshot, es.snapshot());
  }
  
  @Test
  public void summarizeEventDoesNothingForCustomEvent() {
    EventSummarizer es = new EventSummarizer(defaultConfig);
    EventSummarizer.SummaryState snapshot = es.snapshot();
    es.summarizeEvent(eventFactory.newCustomEvent("whatever", user, null));
    
    assertEquals(snapshot, es.snapshot());
  }
  
  @Test
  public void summarizeEventSetsStartAndEndDates() {
    EventSummarizer es = new EventSummarizer(defaultConfig);
    FeatureFlag flag = new FeatureFlagBuilder("key").build();
    eventTimestamp = 2000;
    Event event1 = eventFactory.newFeatureRequestEvent(flag, user, null, null);
    eventTimestamp = 1000;
    Event event2 = eventFactory.newFeatureRequestEvent(flag, user, null, null);
    eventTimestamp = 1500;
    Event event3 = eventFactory.newFeatureRequestEvent(flag, user, null, null);
    es.summarizeEvent(event1);
    es.summarizeEvent(event2);
    es.summarizeEvent(event3);
    EventSummarizer.SummaryOutput data = es.output(es.snapshot());
    
    assertEquals(1000, data.startDate);
    assertEquals(2000, data.endDate);
  }
  
  @Test
  public void summarizeEventIncrementsCounters() {
    EventSummarizer es = new EventSummarizer(defaultConfig);
    FeatureFlag flag1 = new FeatureFlagBuilder("key1").version(11).build();
    FeatureFlag flag2 = new FeatureFlagBuilder("key2").version(22).build();
    String unknownFlagKey = "badkey";
    JsonElement default1 = new JsonPrimitive("default1");
    JsonElement default2 = new JsonPrimitive("default2");
    JsonElement default3 = new JsonPrimitive("default3");
    Event event1 = eventFactory.newFeatureRequestEvent(flag1, user,
        new FeatureFlag.VariationAndValue(1, new JsonPrimitive("value1")), default1);
    Event event2 = eventFactory.newFeatureRequestEvent(flag1, user,
        new FeatureFlag.VariationAndValue(2, new JsonPrimitive("value2")), default1);
    Event event3 = eventFactory.newFeatureRequestEvent(flag2, user,
        new FeatureFlag.VariationAndValue(1, new JsonPrimitive("value99")), default2);
    Event event4 = eventFactory.newFeatureRequestEvent(flag1, user,
        new FeatureFlag.VariationAndValue(1, new JsonPrimitive("value1")), default1);
    Event event5 = eventFactory.newUnknownFeatureRequestEvent(unknownFlagKey, user, default3);
    es.summarizeEvent(event1);
    es.summarizeEvent(event2);
    es.summarizeEvent(event3);
    es.summarizeEvent(event4);
    es.summarizeEvent(event5);
    EventSummarizer.SummaryOutput data = es.output(es.snapshot());
    
    data.features.get(flag1.getKey()).counters.sort(new CounterValueComparator());
    EventSummarizer.CounterData expected1 = new EventSummarizer.CounterData(
        new JsonPrimitive("value1"), flag1.getVersion(), 2, null);
    EventSummarizer.CounterData expected2 = new EventSummarizer.CounterData(
        new JsonPrimitive("value2"), flag1.getVersion(), 1, null);
    EventSummarizer.CounterData expected3 = new EventSummarizer.CounterData(
        new JsonPrimitive("value99"), flag2.getVersion(), 1, null);
    EventSummarizer.CounterData expected4 = new EventSummarizer.CounterData(
        default3, null, 1, true);
    Map<String, EventSummarizer.FlagSummaryData> expectedFeatures = new HashMap<>();
    expectedFeatures.put(flag1.getKey(), new EventSummarizer.FlagSummaryData(default1,
        Arrays.asList(expected1, expected2)));
    expectedFeatures.put(flag2.getKey(), new EventSummarizer.FlagSummaryData(default2,
        Arrays.asList(expected3)));
    expectedFeatures.put(unknownFlagKey, new EventSummarizer.FlagSummaryData(default3,
        Arrays.asList(expected4)));
    assertThat(data.features, equalTo(expectedFeatures));
  }
  
  private static class CounterValueComparator implements Comparator<EventSummarizer.CounterData> {
    public int compare(CounterData o1, CounterData o2) {
      return o1.value.getAsString().compareTo(o2.value.getAsString());
    }
  }
}
