package com.launchdarkly.client;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages the state of summarizable information for the EventProcessor, including the
 * event counters and user deduplication. Note that the methods of this class are
 * deliberately not thread-safe, because they should always be called from EventProcessor's
 * single event-processing thread.
 */
class EventSummarizer {
  private EventsState eventsState;
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
   * Adds this event to our counters, if it is a type of event we need to count.
   * @param event an event
   */
  void summarizeEvent(Event event) {
    if (event instanceof FeatureRequestEvent) {
      FeatureRequestEvent fe = (FeatureRequestEvent)event;
      eventsState.incrementCounter(fe.key, fe.variation, fe.version, fe.value);
      eventsState.noteTimestamp(fe.creationDate);
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
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof EventsState) {
        EventsState o = (EventsState)other;
        return o.counters.equals(counters) && startDate == o.startDate && endDate == o.endDate;
      }
      return true;
    }
    
    @Override
    public int hashCode() {
      return counters.hashCode() + 31 * ((int)startDate + 31 * (int)endDate);
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
      return key.hashCode() + 31 * (variation + 31 * version);
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
    
    CounterData(String key, JsonElement value, Integer version, int count, Boolean unknown) {
      this.key = key;
      this.value = value;
      this.version = version;
      this.count = count;
      this.unknown = unknown;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof CounterData) {
        CounterData o = (CounterData)other;
        return o.key.equals(key) && Objects.equals(value, o.value) && Objects.equals(version, o.version) &&
            o.count == count && Objects.deepEquals(unknown, o.unknown);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return key.hashCode() + 31 * (Objects.hashCode(value) + 31 * (Objects.hashCode(version) + 31 *
          (count + 31 * (Objects.hashCode(unknown)))));
    }
    
    @Override
    public String toString() {
      return "{" + key + ", " + value + ", " + version + ", " + count + ", " + unknown + "}";
    }
  }
  
  static class SummaryOutput {
    final long startDate;
    final long endDate;
    final List<CounterData> counters;
    
    SummaryOutput(long startDate, long endDate, List<CounterData> counters) {
      this.startDate = startDate;
      this.endDate = endDate;
      this.counters = counters;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof SummaryOutput) {
        SummaryOutput o = (SummaryOutput)other;
        return o.startDate == startDate && o.endDate == endDate && o.counters.equals(counters);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return counters.hashCode() + 31 * ((int)startDate + 31 * (int)endDate);
    }
    
    @Override
    public String toString() {
      return "{" + startDate + ", " + endDate + ", " + counters + "}";
    }
  }
}
