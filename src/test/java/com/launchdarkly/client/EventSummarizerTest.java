package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;

import org.junit.Test;

import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
  public void summarizeEventReturnsFalseForIdentifyEvent() {
    EventSummarizer es = new EventSummarizer(defaultConfig);
    Event event = new IdentifyEvent(user);
    assertFalse(es.summarizeEvent(event));
  }
  
  @Test
  public void summarizeEventReturnsFalseForCustomEvent() {
    EventSummarizer es = new EventSummarizer(defaultConfig);
    Event event = new CustomEvent("whatever", user, null);
    assertFalse(es.summarizeEvent(event));
  }
  
  @Test
  public void summarizeEventReturnsTrueForFeatureEventWithTrackEventsFalse() {
    EventSummarizer es = new EventSummarizer(defaultConfig);
    FeatureFlag flag = new FeatureFlagBuilder("key").build();
    Event event = eventFactory.newFeatureRequestEvent(flag, user, null, null);
    assertTrue(es.summarizeEvent(event));
  }
  
  @Test
  public void summarizeEventReturnsFalseForFeatureEventWithTrackEventsTrue() {
    EventSummarizer es = new EventSummarizer(defaultConfig);
    FeatureFlag flag = new FeatureFlagBuilder("key").trackEvents(true).build();
    Event event = eventFactory.newFeatureRequestEvent(flag, user, null, null);
    assertFalse(es.summarizeEvent(event));
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
    
    assertEquals(4, data.counters.size());
    EventSummarizer.CounterData result1 = findCounter(data.counters, flag1.getKey(), "value1");
    assertNotNull(result1);
    assertEquals(flag1.getKey(), result1.key);
    assertEquals(new Integer(flag1.getVersion()), result1.version);
    assertEquals(2, result1.count);
    assertNull(result1.unknown);
    EventSummarizer.CounterData result2 = findCounter(data.counters, flag1.getKey(), "value2");
    assertNotNull(result2);
    assertEquals(flag1.getKey(), result2.key);
    assertEquals(new Integer(flag1.getVersion()), result2.version);
    assertEquals(1, result2.count);
    assertNull(result2.unknown);
    EventSummarizer.CounterData result3 = findCounter(data.counters, flag2.getKey(), "value99");
    assertNotNull(result3);
    assertEquals(flag2.getKey(), result3.key);
    assertEquals(new Integer(flag2.getVersion()), result3.version);
    assertEquals(1, result3.count);
    assertNull(result3.unknown);
    EventSummarizer.CounterData result4 = findCounter(data.counters, unknownFlagKey, null);
    assertNotNull(result4);
    assertEquals(unknownFlagKey, result4.key);
    assertNull(result4.version);
    assertEquals(1, result4.count);
    assertEquals(Boolean.TRUE, result4.unknown);
  }
  
  private EventSummarizer.CounterData findCounter(Iterable<EventSummarizer.CounterData> counters, String key, String value) {
    JsonPrimitive jv = value == null ? null : new JsonPrimitive(value);
    for (EventSummarizer.CounterData c: counters) {
      if (c.key.equals(key) && Objects.equals(c.value, jv)) {
        return c;
      }
    }
    return null;
  }
}
