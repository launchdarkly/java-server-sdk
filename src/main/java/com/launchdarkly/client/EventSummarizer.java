package com.launchdarkly.client;

import com.google.gson.JsonElement;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manages the state of summarizable information for the EventProcessor, including the
 * event counters and user deduplication. Note that the methods of this class are
 * deliberately not thread-safe, because they should always be called from EventProcessor's
 * single event-processing thread.
 */
class EventSummarizer {
  private EventSummary eventsState;
  private final SimpleLRUCache<String, String> userKeys;
  
  EventSummarizer(LDConfig config) {
    this.eventsState = new EventSummary();
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
   * Adds this event to our counters, if it is a type of event we need to count.
   * @param event an event
   */
  void summarizeEvent(Event event) {
    if (event instanceof FeatureRequestEvent) {
      FeatureRequestEvent fe = (FeatureRequestEvent)event;
      eventsState.incrementCounter(fe.key, fe.variation, fe.version, fe.value, fe.defaultVal);
      eventsState.noteTimestamp(fe.creationDate);
    }
  }
  
  /**
   * Returns a snapshot of the current summarized event data, and resets this state.
   * @return the previous event state
   */
  EventSummary snapshot() {
    EventSummary ret = eventsState;
    eventsState = new EventSummary();
    return ret;
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

  static class EventSummary {
    final Map<CounterKey, CounterValue> counters;
    long startDate;
    long endDate;
    
    EventSummary() {
      counters = new HashMap<CounterKey, CounterValue>();
    }
    
    boolean isEmpty() {
      return counters.isEmpty();
    }
    
    void incrementCounter(String flagKey, Integer variation, Integer version, JsonElement flagValue, JsonElement defaultVal) {
      CounterKey key = new CounterKey(flagKey, (variation == null) ? 0 : variation.intValue(),
          (version == null) ? 0 : version.intValue());

      CounterValue value = counters.get(key);
      if (value != null) {
        value.increment();
      } else {
        counters.put(key, new CounterValue(1, flagValue, defaultVal));
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
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof EventSummary) {
        EventSummary o = (EventSummary)other;
        return o.counters.equals(counters) && startDate == o.startDate && endDate == o.endDate;
      }
      return true;
    }
    
    @Override
    public int hashCode() {
      return counters.hashCode() + 31 * ((int)startDate + 31 * (int)endDate);
    }
  }

  static class CounterKey {
    final String key;
    final int variation;
    final int version;
    
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
      return key.hashCode() + 31 * (variation + 31 * version);
    }
    
    @Override
    public String toString() {
      return "(" + key + "," + variation + "," + version + ")";
    }
  }
  
  static class CounterValue {
    int count;
    final JsonElement flagValue;
    final JsonElement defaultVal;
    
    CounterValue(int count, JsonElement flagValue, JsonElement defaultVal) {
      this.count = count;
      this.flagValue = flagValue;
      this.defaultVal = defaultVal;
    }
    
    void increment() {
      count = count + 1;
    }
    
    @Override
    public boolean equals(Object other)
    {
      if (other instanceof CounterValue) {
        CounterValue o = (CounterValue)other;
        return count == o.count && Objects.equals(flagValue, o.flagValue) &&
            Objects.equals(defaultVal, o.defaultVal);
      }
      return false;
    }
    @Override
    public String toString() {
      return "(" + count + "," + flagValue + "," + defaultVal + ")";
    }
  }
}
