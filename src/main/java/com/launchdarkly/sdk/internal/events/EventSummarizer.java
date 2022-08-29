package com.launchdarkly.sdk.internal.events;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
   * Adds information about an evaluation to our counters.
   *
   * @param timestamp the millisecond timestamp
   * @param flagKey the flag key
   * @param flagVersion the flag version, or -1 if the flag is unknown
   * @param variation the result variation, or -1 if none
   * @param value the result value
   * @param defaultValue the application default value
   * @param context the evaluation context
   */
  void summarizeEvent(
      long timestamp,
      String flagKey,
      int flagVersion,
      int variation,
      LDValue value,
      LDValue defaultValue,
      LDContext context
      ) {
    eventsState.incrementCounter(flagKey, flagVersion, variation, value, defaultValue, context);
    eventsState.noteTimestamp(timestamp);
  }
  
  /**
   * Gets the current summarized event data, and resets the EventSummarizer's state to contain
   * a new empty EventSummary.
   * 
   * @return the summary state
   */
  EventSummary getSummaryAndReset() {
    EventSummary ret = eventsState;
    clear();
    return ret;
  }
  
  /**
   * Indicates that we decided not to send the summary values returned by {@link #getSummaryAndReset()},
   * and instead we should return to using the previous state object and keep accumulating data
   * in it. 
   */  
  void restoreTo(EventSummary previousState) {
    eventsState = previousState;
  }

  /**
   * Returns true if there is no summary data in the current state.
   * 
   * @return true if the state is empty
   */
  boolean isEmpty() {
    return eventsState.isEmpty();
  }
  
  void clear() {
    eventsState = new EventSummary();
  }
  
  static final class EventSummary {
    final Map<String, FlagInfo> counters;
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
    
    void incrementCounter(
        String flagKey,
        int flagVersion,
        int variation,
        LDValue flagValue,
        LDValue defaultVal,
        LDContext context
        ) {
      FlagInfo flagInfo = counters.get(flagKey);
      if (flagInfo == null) {
        flagInfo = new FlagInfo(defaultVal, new SimpleIntKeyedMap<>(), new HashSet<>());
        counters.put(flagKey, flagInfo);
      }
      for (int i = 0; i < context.getIndividualContextCount(); i++) {
        flagInfo.contextKinds.add(context.getIndividualContext(i).getKind().toString());
      }
      
      SimpleIntKeyedMap<CounterValue> variations = flagInfo.versionsAndVariations.get(flagVersion);
      if (variations == null) {
        variations = new SimpleIntKeyedMap<>();
        flagInfo.versionsAndVariations.put(flagVersion, variations);
      }
      
      CounterValue value = variations.get(variation);
      if (value == null) {
        variations.put(variation, new CounterValue(1, flagValue));
      } else {
        value.increment();
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

  static final class FlagInfo {
    final LDValue defaultVal;
    final SimpleIntKeyedMap<SimpleIntKeyedMap<CounterValue>> versionsAndVariations;
    final Set<String> contextKinds;
    
    FlagInfo(LDValue defaultVal, SimpleIntKeyedMap<SimpleIntKeyedMap<CounterValue>>  versionsAndVariations,
        Set<String> contextKinds) {
      this.defaultVal = defaultVal;
      this.versionsAndVariations = versionsAndVariations;
      this.contextKinds = contextKinds;
    }
    
    @Override
    public boolean equals(Object other) { // used only in tests
      if (other instanceof FlagInfo) {
        FlagInfo o = (FlagInfo)other;
        return o.defaultVal.equals(this.defaultVal) && o.versionsAndVariations.equals(this.versionsAndVariations) &&
            o.contextKinds.equals(this.contextKinds);
      }
      return false;
    }
    
    @Override
    public int hashCode() { // used only in tests
      return this.defaultVal.hashCode() + 31 * versionsAndVariations.hashCode();
    }
    
    @Override
    public String toString() { // used only in tests
      return "(default=" + defaultVal + ", counters=" + versionsAndVariations + ", contextKinds=" +
        String.join(",", contextKinds) + ")";
    }
  }
  
  static final class CounterValue {
    long count;
    final LDValue flagValue;
    
    CounterValue(long count, LDValue flagValue) {
      this.count = count;
      this.flagValue = flagValue;
    }
    
    void increment() {
      count = count + 1;
    }
    
    @Override
    public boolean equals(Object other) { // used only in tests
      if (other instanceof CounterValue) {
        CounterValue o = (CounterValue)other;
        return count == o.count && Objects.equals(flagValue, o.flagValue);
      }
      return false;
    }
    
    @Override
    public String toString() { // used only in tests
      return "(" + count + "," + flagValue + ")";
    }
  }
  
  // A very simple array-backed structure with map-like semantics for primitive int keys. This
  // is highly specialized for the EventSummarizer use case (which is why it is an inner class
  // of EventSummarizer, to emphasize that it should not be used elsewhere). It makes the
  // following assumptions:
  // - The number of keys will almost always be small: most flags have only a few variations,
  // and most flags will have only one version or a few versions during the lifetime of an
  // event payload. Therefore, we use simple iteration and int comparisons for the keys; the
  // overhead of this is likely less than the overhead of maintaining a hashtable and creating
  // objects for its keys and iterators.
  // - Data will never be deleted from the map after being added (the summarizer simply makes
  // a new map when it's time to start over).
  static final class SimpleIntKeyedMap<T> {
    private static final int INITIAL_CAPACITY = 4;
    
    private int[] keys;
    private Object[] values;
    private int n;
    
    SimpleIntKeyedMap() {
      keys = new int[INITIAL_CAPACITY];
      values = new Object[INITIAL_CAPACITY];
    }
    
    int size() {
      return n;
    }
    
    int capacity() {
      return keys.length;
    }
    
    int keyAt(int index) {
      return keys[index];
    }
    
    @SuppressWarnings("unchecked")
    T valueAt(int index) {
      return (T)values[index]; 
    }
    
    @SuppressWarnings("unchecked")
    T get(int key) {
      for (int i = 0; i < n; i++) {
        if (keys[i] == key) {
          return (T)values[i];
        }
      }
      return null;
    }
    
    SimpleIntKeyedMap<T> put(int key, T value) {
      for (int i = 0; i < n; i++) {
        if (keys[i] == key) {
          values[i] = value;
          return this;
        }
      }
      if (n == keys.length) {
        int[] newKeys = new int[keys.length * 2];
        System.arraycopy(keys, 0, newKeys, 0, n);
        Object[] newValues = new Object[keys.length * 2];
        System.arraycopy(values, 0, newValues, 0, n);
        keys = newKeys;
        values = newValues;
      }
      keys[n] = key;
      values[n] = value;
      n++;
      return this;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) { // used only in tests
      if (o instanceof SimpleIntKeyedMap<?>) {
        SimpleIntKeyedMap<T> other = (SimpleIntKeyedMap<T>)o;
        if (this.n == other.n) {
          for (int i = 0; i < n; i++) {
            T value1 = (T)values[i], value2 = other.get(keys[i]);
            if (!Objects.equals(value1, value2)) {
              return false;
            }
          }
          return true;
        }
      }
      return false;
    }
    
    @Override
    public String toString() { // used only in tests
      StringBuilder s = new StringBuilder("{");
      for (int i = 0; i < n; i++) {
        s.append(keys[i]).append("=").append(values[i] == null ? "null" : values[i].toString());
      }
      s.append("}");
      return s.toString();
    }
  }
}
