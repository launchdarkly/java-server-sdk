package com.launchdarkly.client;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages the state of summarizable information for the EventProcessor, including the
 * event counters and user deduplication.
 */
class EventSummarizer {
  private Map<CounterKey, CounterValue> counters;
  private long startDate;
  private long endDate;
  private long lastKnownPastTime;
  private Set<String> userKeysSeen;
  private int userKeysCapacity;
  
  EventSummarizer(LDConfig config) {
    this.counters = new HashMap<>();
    this.startDate = 0;
    this.endDate = 0;
    this.userKeysSeen = new HashSet<>();
    this.userKeysCapacity = config.userKeysCapacity;
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
    if (userKeysSeen.contains(key)) {
      return true;
    }
    if (userKeysSeen.size() < userKeysCapacity) {
      userKeysSeen.add(key);
    }
    return false;
  }
  
  /**
   * Reset the set of users we've seen.
   */
  void resetUsers() {
    userKeysSeen.clear();
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
    
    CounterKey key = new CounterKey(fe.key, (fe.variation == null) ? 0 : fe.variation.intValue(),
        (fe.version == null) ? 0 : fe.version.intValue());
    
    CounterValue value = counters.get(key);
    if (value != null) {
      value.increment();
    } else {
      counters.put(key, new CounterValue(1, fe.value));
    }
    
    if (startDate == 0 || fe.creationDate < startDate) {
      startDate = fe.creationDate;
    }
    if (fe.creationDate > endDate) {
      endDate = fe.creationDate;
    }
    
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
  
  SummaryOutput flush() {
    List<CounterData> countersOut = new ArrayList<>(counters.size());
    for (Map.Entry<CounterKey, CounterValue> entry: counters.entrySet()) {
      CounterData c = new CounterData(entry.getKey().key,
          entry.getValue().flagValue,
          entry.getKey().version == 0 ? null : entry.getKey().version,
          entry.getValue().count,
          entry.getKey().version == 0 ? true : null);
      countersOut.add(c);
    }
    counters.clear();
    
    SummaryOutput ret = new SummaryOutput(startDate, endDate, countersOut);
    startDate = 0;
    endDate = 0;
    return ret;
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
