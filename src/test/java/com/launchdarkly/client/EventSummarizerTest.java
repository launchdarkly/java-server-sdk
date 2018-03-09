package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
    EventSummarizer.EventsState snapshot = es.snapshot();
    es.summarizeEvent(eventFactory.newIdentifyEvent(user));
    
    assertEquals(snapshot, es.snapshot());
  }
  
  @Test
  public void summarizeEventDoesNothingForCustomEvent() {
    EventSummarizer es = new EventSummarizer(defaultConfig);
    EventSummarizer.EventsState snapshot = es.snapshot();
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
    Event event1 = eventFactory.newFeatureRequestEvent(flag1, user,
        new FeatureFlag.VariationAndValue(1, new JsonPrimitive("value1")), null);
    Event event2 = eventFactory.newFeatureRequestEvent(flag1, user,
        new FeatureFlag.VariationAndValue(2, new JsonPrimitive("value2")), null);
    Event event3 = eventFactory.newFeatureRequestEvent(flag2, user,
        new FeatureFlag.VariationAndValue(1, new JsonPrimitive("value99")), null);
    Event event4 = eventFactory.newFeatureRequestEvent(flag1, user,
        new FeatureFlag.VariationAndValue(1, new JsonPrimitive("value1")), null);
    Event event5 = eventFactory.newUnknownFeatureRequestEvent(unknownFlagKey, user, null);
    es.summarizeEvent(event1);
    es.summarizeEvent(event2);
    es.summarizeEvent(event3);
    es.summarizeEvent(event4);
    es.summarizeEvent(event5);
    EventSummarizer.SummaryOutput data = es.output(es.snapshot());
    
    EventSummarizer.CounterData expected1 = new EventSummarizer.CounterData(flag1.getKey(),
        new JsonPrimitive("value1"), flag1.getVersion(), 2, null);
    EventSummarizer.CounterData expected2 = new EventSummarizer.CounterData(flag1.getKey(),
        new JsonPrimitive("value2"), flag1.getVersion(), 1, null);
    EventSummarizer.CounterData expected3 = new EventSummarizer.CounterData(flag2.getKey(),
        new JsonPrimitive("value99"), flag2.getVersion(), 1, null);
    EventSummarizer.CounterData expected4 = new EventSummarizer.CounterData(unknownFlagKey,
        null, null, 1, true);
    assertThat(data.counters, containsInAnyOrder(expected1, expected2, expected3, expected4));
  }
}
