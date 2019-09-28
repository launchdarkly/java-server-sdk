package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

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
      eventsState.incrementCounter(fe.key, fe.variation, fe.version, fe.value, fe.defaultVal);
      eventsState.noteTimestamp(fe.creationDate);
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
    
    void incrementCounter(String flagKey, Integer variation, Integer version, LDValue flagValue, LDValue defaultVal) {
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
      return true;
    }
    
    @Override
    public int hashCode() {
      return counters.hashCode() + 31 * ((int)startDate + 31 * (int)endDate);
    }
  }

  static final class CounterKey {
    final String key;
    final Integer variation;
    final Integer version;
    
    CounterKey(String key, Integer variation, Integer version) {
      this.key = key;
      this.variation = variation;
      this.version = version;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof CounterKey) {
        CounterKey o = (CounterKey)other;
        return o.key.equals(this.key) && Objects.equals(o.variation, this.variation) &&
            Objects.equals(o.version, this.version);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return key.hashCode() + 31 * (Objects.hashCode(variation) + 31 * Objects.hashCode(version));
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
