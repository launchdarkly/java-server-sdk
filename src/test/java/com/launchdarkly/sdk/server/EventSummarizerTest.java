package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.EventSummarizer.CounterKey;
import com.launchdarkly.sdk.server.EventSummarizer.CounterValue;
import com.launchdarkly.sdk.server.EventSummarizer.EventSummary;
import com.launchdarkly.sdk.server.interfaces.Event;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestUtil.simpleEvaluation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class EventSummarizerTest {
  private static final LDUser user = new LDUser.Builder("key").build();
  
  private long eventTimestamp;
  private EventFactory eventFactory = new EventFactory.Default(false, () -> eventTimestamp);

  @Test
  public void summarizerCanBeCleared() {
    EventSummarizer es = new EventSummarizer();
    assertTrue(es.snapshot().isEmpty());
    
    DataModel.FeatureFlag flag = flagBuilder("key").build();
    Event event = eventFactory.newFeatureRequestEvent(flag, user, null, null);
    es.summarizeEvent(event);
    
    assertFalse(es.snapshot().isEmpty());
    
    es.clear();
    
    assertTrue(es.snapshot().isEmpty());
  }
  
  @Test
  public void summarizeEventDoesNothingForIdentifyEvent() {
    EventSummarizer es = new EventSummarizer();
    EventSummarizer.EventSummary snapshot = es.snapshot();
    es.summarizeEvent(eventFactory.newIdentifyEvent(user));
    
    assertEquals(snapshot, es.snapshot());
  }
  
  @Test
  public void summarizeEventDoesNothingForCustomEvent() {
    EventSummarizer es = new EventSummarizer();
    EventSummarizer.EventSummary snapshot = es.snapshot();
    es.summarizeEvent(eventFactory.newCustomEvent("whatever", user, null, null));
    
    assertEquals(snapshot, es.snapshot());
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
    EventSummarizer.EventSummary data = es.snapshot();
    
    assertEquals(1000, data.startDate);
    assertEquals(2000, data.endDate);
  }
  
  @Test
  public void summarizeEventIncrementsCounters() {
    EventSummarizer es = new EventSummarizer();
    DataModel.FeatureFlag flag1 = flagBuilder("key1").version(11).build();
    DataModel.FeatureFlag flag2 = flagBuilder("key2").version(22).build();
    String unknownFlagKey = "badkey";
    Event event1 = eventFactory.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(1, LDValue.of("value1")), LDValue.of("default1"));
    Event event2 = eventFactory.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(2, LDValue.of("value2")), LDValue.of("default1"));
    Event event3 = eventFactory.newFeatureRequestEvent(flag2, user,
        simpleEvaluation(1, LDValue.of("value99")), LDValue.of("default2"));
    Event event4 = eventFactory.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(1, LDValue.of("value1")), LDValue.of("default1"));
    Event event5 = eventFactory.newUnknownFeatureRequestEvent(unknownFlagKey, user, LDValue.of("default3"), EvaluationReason.ErrorKind.FLAG_NOT_FOUND);
    es.summarizeEvent(event1);
    es.summarizeEvent(event2);
    es.summarizeEvent(event3);
    es.summarizeEvent(event4);
    es.summarizeEvent(event5);
    EventSummarizer.EventSummary data = es.snapshot();
    
    Map<EventSummarizer.CounterKey, EventSummarizer.CounterValue> expected = new HashMap<>();
    expected.put(new EventSummarizer.CounterKey(flag1.getKey(), 1, flag1.getVersion()),
        new EventSummarizer.CounterValue(2, LDValue.of("value1"), LDValue.of("default1")));
    expected.put(new EventSummarizer.CounterKey(flag1.getKey(), 2, flag1.getVersion()),
        new EventSummarizer.CounterValue(1, LDValue.of("value2"), LDValue.of("default1")));
    expected.put(new EventSummarizer.CounterKey(flag2.getKey(), 1, flag2.getVersion()),
        new EventSummarizer.CounterValue(1, LDValue.of("value99"), LDValue.of("default2")));
    expected.put(new EventSummarizer.CounterKey(unknownFlagKey, -1, -1),
        new EventSummarizer.CounterValue(1, LDValue.of("default3"), LDValue.of("default3")));
    assertThat(data.counters, equalTo(expected));
  }
  
  @Test
  public void counterKeyEquality() {
    // This must be correct in order for CounterKey to be used as a map key.
    CounterKey key1 = new CounterKey("a", 1, 10);
    CounterKey key2 = new CounterKey("a", 1, 10);
    assertEquals(key1, key2);
    assertEquals(key2, key1);
    assertEquals(key1.hashCode(), key2.hashCode());
    
    for (CounterKey notEqualValue: new CounterKey[] {
        new CounterKey("b", 1, 10),
        new CounterKey("a", 2, 10),
        new CounterKey("a", 1, 11)
    }) {
      assertNotEquals(key1, notEqualValue);
      assertNotEquals(notEqualValue, key1);
      assertNotEquals(key1.hashCode(), notEqualValue.hashCode());
    }
    
    assertNotEquals(key1, null);
    assertNotEquals(key1, "x");
  }
  
  // The following implementations are used only in debug/test code, but may as well test them
  
  @Test
  public void counterKeyToString() {
    assertEquals("(a,1,10)", new CounterKey("a", 1, 10).toString());
  }
  
  @Test
  public void counterValueEquality() {
    CounterValue value1 = new CounterValue(1, LDValue.of("a"), LDValue.of("d"));
    CounterValue value2 = new CounterValue(1, LDValue.of("a"), LDValue.of("d"));
    assertEquals(value1, value2);
    assertEquals(value2, value1);
    
    for (CounterValue notEqualValue: new CounterValue[] {
        new CounterValue(2, LDValue.of("a"), LDValue.of("d")),
        new CounterValue(1, LDValue.of("b"), LDValue.of("d")),
        new CounterValue(1, LDValue.of("a"), LDValue.of("e"))
    }) {
      assertNotEquals(value1, notEqualValue);
      assertNotEquals(notEqualValue, value1);
      
      assertNotEquals(value1, null);
      assertNotEquals(value1, "x");
    }
  }
  
  @Test
  public void counterValueToString() {
    assertEquals("(1,\"a\",\"d\")", new CounterValue(1, LDValue.of("a"), LDValue.of("d")).toString());
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
}
