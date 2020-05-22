package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.Event;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestUtil.simpleEvaluation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class EventSummarizerTest {
  private static final LDUser user = new LDUser.Builder("key").build();
  
  private long eventTimestamp;
  private EventFactory eventFactory = new EventFactory(false, false) {
    @Override
    protected long getTimestamp() {
      return eventTimestamp;
    }
  };
  
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
}
