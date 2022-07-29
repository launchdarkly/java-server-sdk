package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;

import org.junit.Test;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.testhelpers.ConcurrentHelpers.assertNoMoreValues;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.awaitValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class FlagTrackerImplTest extends BaseTest {

  @Test
  public void flagChangeListeners() throws Exception {
    String flagKey = "flagkey";
    EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> broadcaster =
        EventBroadcasterImpl.forFlagChangeEvents(TestComponents.sharedExecutor, testLogger);
    
    FlagTrackerImpl tracker = new FlagTrackerImpl(broadcaster, null);
    
    BlockingQueue<FlagChangeEvent> eventSink1 = new LinkedBlockingQueue<>();
    BlockingQueue<FlagChangeEvent> eventSink2 = new LinkedBlockingQueue<>();
    FlagChangeListener listener1 = eventSink1::add;
    FlagChangeListener listener2 = eventSink2::add; // need to capture the method reference in a variable so it's the same instance when we unregister it
    tracker.addFlagChangeListener(listener1);
    tracker.addFlagChangeListener(listener2);
      
    assertNoMoreValues(eventSink1, 100, TimeUnit.MILLISECONDS);
    assertNoMoreValues(eventSink2, 100, TimeUnit.MILLISECONDS);

    broadcaster.broadcast(new FlagChangeEvent(flagKey));
      
    FlagChangeEvent event1 = awaitValue(eventSink1, 1, TimeUnit.SECONDS);
    FlagChangeEvent event2 = awaitValue(eventSink2, 1, TimeUnit.SECONDS);
    assertThat(event1.getKey(), equalTo("flagkey"));
    assertThat(event2.getKey(), equalTo("flagkey"));
    assertNoMoreValues(eventSink1, 100, TimeUnit.MILLISECONDS);
    assertNoMoreValues(eventSink2, 100, TimeUnit.MILLISECONDS);
      
    tracker.removeFlagChangeListener(listener1);
      
    broadcaster.broadcast(new FlagChangeEvent(flagKey));
  
    FlagChangeEvent event3 = awaitValue(eventSink2, 1, TimeUnit.SECONDS);
    assertThat(event3.getKey(), equalTo(flagKey));
    assertNoMoreValues(eventSink1, 100, TimeUnit.MILLISECONDS);
    assertNoMoreValues(eventSink2, 100, TimeUnit.MILLISECONDS);
  }

  @Test
  public void flagValueChangeListener() throws Exception {
    String flagKey = "important-flag";
    LDContext user = LDContext.create("important-user");
    LDContext otherUser = LDContext.create("unimportant-user");
    EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> broadcaster =
        EventBroadcasterImpl.forFlagChangeEvents(TestComponents.sharedExecutor, testLogger);
    Map<Map.Entry<String, LDContext>, LDValue> resultMap = new HashMap<>();
    
    FlagTrackerImpl tracker = new FlagTrackerImpl(broadcaster,
        (k, u) -> LDValue.normalize(resultMap.get(new AbstractMap.SimpleEntry<>(k, u))));

    resultMap.put(new AbstractMap.SimpleEntry<>(flagKey, user), LDValue.of(false));
    resultMap.put(new AbstractMap.SimpleEntry<>(flagKey, otherUser), LDValue.of(false));
    
    BlockingQueue<FlagValueChangeEvent> eventSink1 = new LinkedBlockingQueue<>();
    BlockingQueue<FlagValueChangeEvent> eventSink2 = new LinkedBlockingQueue<>();
    BlockingQueue<FlagValueChangeEvent> eventSink3 = new LinkedBlockingQueue<>();
    tracker.addFlagValueChangeListener(flagKey, user, eventSink1::add);
    FlagChangeListener listener2 = tracker.addFlagValueChangeListener(flagKey, user, eventSink2::add);
    tracker.removeFlagChangeListener(listener2); // just verifying that the remove method works
    tracker.addFlagValueChangeListener(flagKey, otherUser, eventSink3::add);
      
    assertNoMoreValues(eventSink1, 100, TimeUnit.MILLISECONDS);
    assertNoMoreValues(eventSink2, 100, TimeUnit.MILLISECONDS);
    assertNoMoreValues(eventSink3, 100, TimeUnit.MILLISECONDS);
    
    // make the flag true for the first user only, and broadcast a flag change event
    resultMap.put(new AbstractMap.SimpleEntry<>(flagKey, user), LDValue.of(true));
    broadcaster.broadcast(new FlagChangeEvent(flagKey));
      
    // eventSink1 receives a value change event
    FlagValueChangeEvent event1 = awaitValue(eventSink1, 1, TimeUnit.SECONDS);
    assertThat(event1.getKey(), equalTo(flagKey));
    assertThat(event1.getOldValue(), equalTo(LDValue.of(false)));
    assertThat(event1.getNewValue(), equalTo(LDValue.of(true)));
    assertNoMoreValues(eventSink1, 100, TimeUnit.MILLISECONDS);
    
    // eventSink2 doesn't receive one, because it was unregistered
    assertNoMoreValues(eventSink2, 100, TimeUnit.MILLISECONDS);
    
    // eventSink3 doesn't receive one, because the flag's value hasn't changed for otherUser
    assertNoMoreValues(eventSink2, 100, TimeUnit.MILLISECONDS);
  }
}
