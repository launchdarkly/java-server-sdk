package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.EventSummarizer.CounterValue;
import com.launchdarkly.sdk.server.EventSummarizer.EventSummary;
import com.launchdarkly.sdk.server.EventSummarizer.FlagInfo;
import com.launchdarkly.sdk.server.EventSummarizer.SimpleIntKeyedMap;
import com.launchdarkly.sdk.server.interfaces.Event;

import org.junit.Test;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestUtil.simpleEvaluation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class EventSummarizerTest {
  private static final LDUser user = new LDUser.Builder("key").build();
  
  private long eventTimestamp;
  private EventFactory eventFactory = new EventFactory.Default(false, () -> eventTimestamp);

  @Test
  public void summarizerCanBeCleared() {
    EventSummarizer es = new EventSummarizer();
    assertTrue(es.isEmpty());
    
    DataModel.FeatureFlag flag = flagBuilder("key").build();
    Event event = eventFactory.newFeatureRequestEvent(flag, user, null, null);
    es.summarizeEvent(event);
    
    assertFalse(es.isEmpty());
    
    es.clear();
    
    assertTrue(es.isEmpty());
  }
  
  @Test
  public void summarizeEventDoesNothingForIdentifyEvent() {
    EventSummarizer es = new EventSummarizer();
    es.summarizeEvent(eventFactory.newIdentifyEvent(user));
    assertTrue(es.isEmpty());
  }
  
  @Test
  public void summarizeEventDoesNothingForCustomEvent() {
    EventSummarizer es = new EventSummarizer();
    es.summarizeEvent(eventFactory.newCustomEvent("whatever", user, null, null));
    assertTrue(es.isEmpty());
  }
  
  @Test
  public void summarizeEventSetsStartAndEndDates() {
    EventSummarizer es = new EventSummarizer();
    DataModel.FeatureFlag flag = flagBuilder("key").build();
    eventTimestamp = 2000;
    Event event1 = eventFactory.newFeatureRequestEvent(flag, user, null, null);
    eventTimestamp = 1000;
    Event event2 = eventFactory.newFeatureRequestEvent(flag, user, null, null);
    eventTimestamp = 1500;
    Event event3 = eventFactory.newFeatureRequestEvent(flag, user, null, null);
    es.summarizeEvent(event1);
    es.summarizeEvent(event2);
    es.summarizeEvent(event3);
    EventSummarizer.EventSummary data = es.getSummaryAndReset();
    
    assertEquals(1000, data.startDate);
    assertEquals(2000, data.endDate);
  }
  
  @Test
  public void summarizeEventIncrementsCounters() {
    EventSummarizer es = new EventSummarizer();
    DataModel.FeatureFlag flag1 = flagBuilder("key1").version(11).build();
    DataModel.FeatureFlag flag2 = flagBuilder("key2").version(22).build();
    String unknownFlagKey = "badkey";
    LDValue value1 = LDValue.of("value1"), value2 = LDValue.of("value2"), value99 = LDValue.of("value99"),
        default1 = LDValue.of("default1"), default2 = LDValue.of("default2"), default3 = LDValue.of("default3");
    Event event1 = eventFactory.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(1, value1), default1);
    Event event2 = eventFactory.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(2, value2), default1);
    Event event3 = eventFactory.newFeatureRequestEvent(flag2, user,
        simpleEvaluation(1, value99), default2);
    Event event4 = eventFactory.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(1, value1), default1);
    Event event5 = eventFactory.newUnknownFeatureRequestEvent(unknownFlagKey, user, default3, EvaluationReason.ErrorKind.FLAG_NOT_FOUND);
    es.summarizeEvent(event1);
    es.summarizeEvent(event2);
    es.summarizeEvent(event3);
    es.summarizeEvent(event4);
    es.summarizeEvent(event5);
    EventSummarizer.EventSummary data = es.getSummaryAndReset();
    
    assertThat(data.counters, equalTo(ImmutableMap.<String, FlagInfo>builder()
        .put(flag1.getKey(), new FlagInfo(default1,
            new SimpleIntKeyedMap<SimpleIntKeyedMap<CounterValue>>()
              .put(flag1.getVersion(), new SimpleIntKeyedMap<CounterValue>()
                    .put(1, new CounterValue(2, value1))
                    .put(2, new CounterValue(1, value2))
                    )))
        .put(flag2.getKey(), new FlagInfo(default2,
            new SimpleIntKeyedMap<SimpleIntKeyedMap<CounterValue>>()
              .put(flag2.getVersion(), new SimpleIntKeyedMap<CounterValue>()
                    .put(1, new CounterValue(1, value99))
                    )))
        .put(unknownFlagKey, new FlagInfo(default3,
            new SimpleIntKeyedMap<SimpleIntKeyedMap<CounterValue>>()
              .put(-1, new SimpleIntKeyedMap<CounterValue>()
                    .put(-1, new CounterValue(1, default3))
                    )))
        .build()));
  }
  
  // The following implementations are used only in debug/test code, but may as well test them
  
  @Test
  public void counterValueEquality() {
    CounterValue value1 = new CounterValue(1, LDValue.of("a"));
    CounterValue value2 = new CounterValue(1, LDValue.of("a"));
    assertEquals(value1, value2);
    assertEquals(value2, value1);
    
    for (CounterValue notEqualValue: new CounterValue[] {
        new CounterValue(2, LDValue.of("a")),
        new CounterValue(1, LDValue.of("b"))
    }) {
      assertNotEquals(value1, notEqualValue);
      assertNotEquals(notEqualValue, value1);
      
      assertNotEquals(value1, null);
      assertNotEquals(value1, "x");
    }
  }
  
  @Test
  public void counterValueToString() {
    assertEquals("(1,\"a\")", new CounterValue(1, LDValue.of("a")).toString());
  }
  
  @Test
  public void eventSummaryEquality() {
    String key1 = "key1", key2 = "key2";
    int variation1 = 0, variation2 = 1, variation3 = 2, version1 = 10, version2 = 20;
    LDValue value1 = LDValue.of(1), value2 = LDValue.of(2), value3 = LDValue.of(3),
        default1 = LDValue.of(-1), default2 = LDValue.of(-2);
    EventSummary es1 = new EventSummary();
    es1.noteTimestamp(1000);
    es1.incrementCounter(key1, variation1, version1, value1, default1);
    es1.incrementCounter(key1, variation1, version1, value1, default1);
    es1.incrementCounter(key1, variation2, version2, value2, default1);
    es1.incrementCounter(key2, variation3, version2, value3, default2);
    es1.noteTimestamp(2000);

    EventSummary es2 = new EventSummary(); // same operations in different order
    es2.noteTimestamp(1000);
    es2.incrementCounter(key2, variation3, version2, value3, default2);
    es2.incrementCounter(key1, variation1, version1, value1, default1);
    es2.incrementCounter(key1, variation2, version2, value2, default1);
    es2.incrementCounter(key1, variation1, version1, value1, default1);
    es2.noteTimestamp(2000);

    EventSummary es3 = new EventSummary(); // same operations with different start time
    es3.noteTimestamp(1100);
    es3.incrementCounter(key2, variation3, version2, value3, default2);
    es3.incrementCounter(key1, variation1, version1, value1, default1);
    es3.incrementCounter(key1, variation2, version2, value2, default1);
    es3.incrementCounter(key1, variation1, version1, value1, default1);
    es3.noteTimestamp(2000);

    EventSummary es4 = new EventSummary(); // same operations with different end time
    es4.noteTimestamp(1000);
    es4.incrementCounter(key2, variation3, version2, value3, default2);
    es4.incrementCounter(key1, variation1, version1, value1, default1);
    es4.incrementCounter(key1, variation2, version2, value2, default1);
    es4.incrementCounter(key1, variation1, version1, value1, default1);
    es4.noteTimestamp(1900);

    assertEquals(es1, es2);
    assertEquals(es2, es1);
    
    assertEquals(0, es1.hashCode()); // see comment on hashCode
    
    assertNotEquals(es1, es3);
    assertNotEquals(es1, es4);
    
    assertNotEquals(es1, null);
    assertNotEquals(es1, "x");
  }
  
  @Test
  public void simpleIntKeyedMapBehavior() {
    // Tests the behavior of the inner class that we use instead of a Map.
    SimpleIntKeyedMap<String> m = new SimpleIntKeyedMap<>();
    int initialCapacity = m.capacity();
    
    assertEquals(0, m.size());
    assertNotEquals(0, initialCapacity);
    assertNull(m.get(1));
    
    for (int i = 0; i < initialCapacity; i++) {
      m.put(i * 100, "value" + i);
    }
    
    assertEquals(initialCapacity, m.size());
    assertEquals(initialCapacity, m.capacity());
    
    for (int i = 0; i < initialCapacity; i++) {
      assertEquals("value" + i, m.get(i * 100));
    }
    assertNull(m.get(33));
    
    m.put(33, "other");
    assertNotEquals(initialCapacity, m.capacity());
    assertEquals(initialCapacity + 1, m.size());
    assertEquals("other", m.get(33));
  }
}
