package com.launchdarkly.client;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the state of summarizable information for the EventProcessor, including the
 * event counters and user deduplication.
 */
class EventSummarizer {
  private EventsState eventsState;
  private long lastKnownPastTime;
  private final SimpleLRUCache<String, String> userKeys;
  
  EventSummarizer(LDConfig config) {
    this.eventsState = new EventsState();
    this.userKeys = new SimpleLRUCache<String, String>(config.userKeysCapacity);
  }
  
  /**
   * Add to the set of users we've noticed, and return true if the user was already known to us.
   * @param user a user
   * @return true if we've already seen this user key
   */
  boolean noticeUser(LDUser user) {
    if (user == null || user.getKey() == null) {
      return false;
    }
    String key = user.getKeyAsString();
    return userKeys.put(key, key) != null;
  }
  
  /**
   * Reset the set of users we've seen.
   */
  void resetUsers() {
    userKeys.clear();
  }
  
  /**
   * Check whether this is a kind of event that we should summarize; if so, add it to our
   * counters and return true. False means that the event should be sent individually.
   * @param event an event
   * @return true if we summarized the event
   */
  boolean summarizeEvent(Event event) {
    if (!(event instanceof FeatureRequestEvent)) {
      return false;
    }
    FeatureRequestEvent fe = (FeatureRequestEvent)event;
    if (fe.trackEvents) {
      return false;
    }
    
    if (fe.debugEventsUntilDate != null) {
      // The "last known past time" comes from the last HTTP response we got from the server.
      // In case the client's time is set wrong, at least we know that any expiration date
      // earlier than that point is definitely in the past.
      if (fe.debugEventsUntilDate > lastKnownPastTime &&
          fe.debugEventsUntilDate > System.currentTimeMillis()) {
        return false;
      }
    }
        
    eventsState.incrementCounter(fe.key, fe.variation, fe.version, fe.value);
    eventsState.noteTimestamp(fe.creationDate);
    
    return true;
  }
  
  /**
   * Marks the given timestamp (received from the server) as being in the past, in case the
   * client-side time is unreliable.
   * @param t a timestamp
   */
  void setLastKnownPastTime(long t) {
    if (lastKnownPastTime < t) {
      lastKnownPastTime = t;
    }
  }
  
  /**
   * Returns a snapshot of the current summarized event data, and resets this state.
   * @return the previous event state
   */
  EventsState snapshot() {
    EventsState ret = eventsState;
    eventsState = new EventsState();
    return ret;
  }

  /**
   * Transforms the summary data into the format used for event sending.
   * @param snapshot the data obtained from {@link #snapshot()}
   * @return the formatted output
   */
  SummaryOutput output(EventsState snapshot) {
    List<CounterData> countersOut = new ArrayList<>(snapshot.counters.size());
    for (Map.Entry<CounterKey, CounterValue> entry: snapshot.counters.entrySet()) {
      CounterData c = new CounterData(entry.getKey().key,
          entry.getValue().flagValue,
          entry.getKey().version == 0 ? null : entry.getKey().version,
          entry.getValue().count,
          entry.getKey().version == 0 ? true : null);
      countersOut.add(c);
    }
    return new SummaryOutput(snapshot.startDate, snapshot.endDate, countersOut);
  }
  
  static class EventsState {
    final Map<CounterKey, CounterValue> counters;
    long startDate;
    long endDate;
    
    EventsState() {
      counters = new HashMap<CounterKey, CounterValue>();
    }
    
    void incrementCounter(String flagKey, Integer variation, Integer version, JsonElement flagValue) {
      CounterKey key = new CounterKey(flagKey, (variation == null) ? 0 : variation.intValue(),
          (version == null) ? 0 : version.intValue());

      CounterValue value = counters.get(key);
      if (value != null) {
        value.increment();
      } else {
        counters.put(key, new CounterValue(1, flagValue));
      }
    }
    
    void noteTimestamp(long time) {
      if (startDate == 0 || time < startDate) {
        startDate = time;
      }
      if (time > endDate) {
        endDate = time;
      }
    }
  }
  
  private static class CounterKey {
    private final String key;
    private final int variation;
    private final int version;
    
    CounterKey(String key, int variation, int version) {
      this.key = key;
      this.variation = variation;
      this.version = version;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof CounterKey) {
        CounterKey o = (CounterKey)other;
        return o.key.equals(this.key) && o.variation == this.variation && o.version == this.version;
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return key.hashCode() + (variation + (version * 31) * 31);
    }
  }
  
  @SuppressWarnings("serial")
  private static class SimpleLRUCache<K, V> extends LinkedHashMap<K, V> {
    // http://chriswu.me/blog/a-lru-cache-in-10-lines-of-java/
    private final int capacity;
    
    SimpleLRUCache(int capacity) {
      super(16, 0.75f, true);
      this.capacity = capacity;
    }
    
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
      return size() > capacity;
    }
  }
  
  private static class CounterValue {
    private int count;
    private JsonElement flagValue;
    
    CounterValue(int count, JsonElement flagValue) {
      this.count = count;
      this.flagValue = flagValue;
    }
    
    void increment() {
      count = count + 1;
    }
  }
  
  static class CounterData {
    final String key;
    final JsonElement value;
    final Integer version;
    final int count;
    final Boolean unknown;
    
    private CounterData(String key, JsonElement value, Integer version, int count, Boolean unknown) {
      this.key = key;
      this.value = value;
      this.version = version;
      this.count = count;
      this.unknown = unknown;
    }
  }
  
  static class SummaryOutput {
    final long startDate;
    final long endDate;
    final List<CounterData> counters;
    
    private SummaryOutput(long startDate, long endDate, List<CounterData> counters) {
      this.startDate = startDate;
      this.endDate = endDate;
      this.counters = counters;
    }
  }
}
