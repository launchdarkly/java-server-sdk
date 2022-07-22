package com.launchdarkly.sdk.internal.events;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.internal.events.EventSummarizer;
import com.launchdarkly.sdk.internal.events.EventSummarizer.CounterValue;
import com.launchdarkly.sdk.internal.events.EventSummarizer.EventSummary;
import com.launchdarkly.sdk.internal.events.EventSummarizer.FlagInfo;
import com.launchdarkly.sdk.internal.events.EventSummarizer.SimpleIntKeyedMap;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class EventSummarizerTest {
  private static final LDContext context = LDContext.create("key");
  
  @Test
  public void summarizerCanBeCleared() {
    EventSummarizer es = new EventSummarizer();
    assertTrue(es.isEmpty());
    
    es.summarizeEvent(1000, "flagkey", 1, 0, LDValue.ofNull(), LDValue.ofNull(), context);
    
    assertFalse(es.isEmpty());
    
    es.clear();
    
    assertTrue(es.isEmpty());
  }
  
  @Test
  public void summarizeEventSetsStartAndEndDates() {
    EventSummarizer es = new EventSummarizer();

    for (long timestamp: new long[] { 2000, 1000, 1500 }) {
      es.summarizeEvent(timestamp, "flagkey", 1, 0, LDValue.ofNull(), LDValue.ofNull(), context);
    }

    EventSummarizer.EventSummary data = es.getSummaryAndReset();
    
    assertEquals(1000, data.startDate);
    assertEquals(2000, data.endDate);
  }
  
  @Test
  public void summarizeEventIncrementsCounters() {
    EventSummarizer es = new EventSummarizer();
    String flagKey1 = "key1", flagKey2 = "key2", unknownFlagKey = "badkey";
    int flagVersion1 = 11, flagVersion2 = 22;
    LDValue value1 = LDValue.of("value1"), value2 = LDValue.of("value2"), value99 = LDValue.of("value99"),
        default1 = LDValue.of("default1"), default2 = LDValue.of("default2"), default3 = LDValue.of("default3");
    LDContext multiKindContext = LDContext.createMulti(
        context, LDContext.create(ContextKind.of("kind2"), "key2"));
    long timestamp = 1000;
    
    es.summarizeEvent(timestamp, flagKey1, flagVersion1, 1, value1, default1, context);
    es.summarizeEvent(timestamp, flagKey1, flagVersion1, 2, value2, default1, context);
    es.summarizeEvent(timestamp, flagKey2, flagVersion2, 1, value99, default2, multiKindContext);
    es.summarizeEvent(timestamp, flagKey1, flagVersion1, 1, value1, default1, context);
    es.summarizeEvent(timestamp, unknownFlagKey, -1, -1, default3, default3, context);

    EventSummarizer.EventSummary data = es.getSummaryAndReset();
    
    assertThat(data.counters, equalTo(ImmutableMap.<String, FlagInfo>builder()
        .put(flagKey1, new FlagInfo(default1,
            new SimpleIntKeyedMap<SimpleIntKeyedMap<CounterValue>>()
              .put(flagVersion1, new SimpleIntKeyedMap<CounterValue>()
                    .put(1, new CounterValue(2, value1))
                    .put(2, new CounterValue(1, value2))
                    ),
            ImmutableSet.of("user")))
        .put(flagKey2, new FlagInfo(default2,
            new SimpleIntKeyedMap<SimpleIntKeyedMap<CounterValue>>()
              .put(flagVersion2, new SimpleIntKeyedMap<CounterValue>()
                    .put(1, new CounterValue(1, value99))
                    ),
            ImmutableSet.of("user", "kind2")))
        .put(unknownFlagKey, new FlagInfo(default3,
            new SimpleIntKeyedMap<SimpleIntKeyedMap<CounterValue>>()
              .put(-1, new SimpleIntKeyedMap<CounterValue>()
                    .put(-1, new CounterValue(1, default3))
                    ),
            ImmutableSet.of("user")))
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
    
    EventSummarizer es1 = new EventSummarizer();
    es1.summarizeEvent(1000, key1, version1, variation1, value1, default1, context);
    es1.summarizeEvent(1000, key1, version1, variation1, value1, default1, context);
    es1.summarizeEvent(1000, key1, version2, variation2, value2, default1, context);
    es1.summarizeEvent(2000, key2, version2, variation3, value3, default2, context);

    EventSummarizer es2 = new EventSummarizer(); // same operations in different order
    es2.summarizeEvent(2000, key2, version2, variation3, value3, default2, context);
    es2.summarizeEvent(1000, key1, version1, variation1, value1, default1, context);
    es2.summarizeEvent(1000, key1, version2, variation2, value2, default1, context);
    es2.summarizeEvent(1000, key1, version1, variation1, value1, default1, context);

    EventSummarizer es3 = new EventSummarizer(); // same operations with different start time
    es3.summarizeEvent(1100, key1, version1, variation1, value1, default1, context);
    es3.summarizeEvent(1100, key1, version1, variation1, value1, default1, context);
    es3.summarizeEvent(1100, key1, version2, variation2, value2, default1, context);
    es3.summarizeEvent(2000, key2, version2, variation3, value3, default2, context);

    EventSummarizer es4 = new EventSummarizer(); // same operations with different end time
    es4.summarizeEvent(1000, key1, version1, variation1, value1, default1, context);
    es4.summarizeEvent(1000, key1, version1, variation1, value1, default1, context);
    es4.summarizeEvent(1000, key1, version2, variation2, value2, default1, context);
    es4.summarizeEvent(2100, key2, version2, variation3, value3, default2, context);

    EventSummary summary1 = es1.getSummaryAndReset();
    EventSummary summary2 = es2.getSummaryAndReset();
    EventSummary summary3 = es3.getSummaryAndReset();
    EventSummary summary4 = es4.getSummaryAndReset();
    
    assertEquals(summary1, summary2);
    assertEquals(summary2, summary1);
    
    assertEquals(0, summary1.hashCode()); // see comment on hashCode
    
    assertNotEquals(summary1, summary3);
    assertNotEquals(summary1, summary4);
    
    assertNotEquals(summary1, null);
    assertNotEquals(summary1, "x");
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
