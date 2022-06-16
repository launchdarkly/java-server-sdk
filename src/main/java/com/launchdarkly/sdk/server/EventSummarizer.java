package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.subsystems.Event;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manages the state of summarizable information for the EventProcessor. Note that the
 * methods of this class are deliberately not thread-safe, because they should always
 * be called from EventProcessor's single message-processing thread.
 */
final class EventSummarizer {
  private EventSummary eventsState;
  
  EventSummarizer() {
    this.eventsState = new EventSummary();
  }
  
  /**
   * Adds this event to our counters, if it is a type of event we need to count.
   * @param event an event
   */
  void summarizeEvent(Event event) {
    if (event instanceof Event.FeatureRequest) {
      Event.FeatureRequest fe = (Event.FeatureRequest)event;
      eventsState.incrementCounter(fe.getKey(), fe.getVariation(), fe.getVersion(), fe.getValue(), fe.getDefaultVal());
      eventsState.noteTimestamp(fe.getCreationDate());
    }
  }
  
  /**
   * Returns a snapshot of the current summarized event data.
   * @return the summary state
   */
  EventSummary snapshot() {
    return new EventSummary(eventsState);
  }
  
  /**
   * Resets the summary counters.
   */
  void clear() {
    eventsState = new EventSummary();
  }
  
  static final class EventSummary {
    final Map<CounterKey, CounterValue> counters;
    long startDate;
    long endDate;
    
    EventSummary() {
      counters = new HashMap<>();
    }

    EventSummary(EventSummary from) {
      counters = new HashMap<>(from.counters);
      startDate = from.startDate;
      endDate = from.endDate;
    }
    
    boolean isEmpty() {
      return counters.isEmpty();
    }
    
    void incrementCounter(String flagKey, int variation, int version, LDValue flagValue, LDValue defaultVal) {
      CounterKey key = new CounterKey(flagKey, variation, version);

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
      return false;
    }
    
    @Override
    public int hashCode() {
      // We can't make meaningful hash codes for EventSummary, because the same counters could be
      // represented differently in our Map. It doesn't matter because there's no reason to use an
      // EventSummary instance as a hash key.
      return 0;
    }
  }

  static final class CounterKey {
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
        return o.key.equals(this.key) && o.variation == this.variation &&
            o.version == this.version;
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
  
  static final class CounterValue {
    long count;
    final LDValue flagValue;
    final LDValue defaultVal;
    
    CounterValue(long count, LDValue flagValue, LDValue defaultVal) {
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
