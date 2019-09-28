package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.launchdarkly.client.EventSummarizer.CounterKey;
import com.launchdarkly.client.EventSummarizer.CounterValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for data structures that we send in an event payload, which are somewhat
 * different in shape from the originating events. Also defines all of its own subclasses
 * and the class that constructs them. These are implementation details used only by
 * DefaultEventProcessor and related classes, so they are all package-private.
 */
abstract class EventOutput {
  @SuppressWarnings("unused")
  private final String kind;
  
  protected EventOutput(String kind) {
    this.kind = kind;
  }
  
  static class EventOutputWithTimestamp extends EventOutput {
    @SuppressWarnings("unused")
    private final long creationDate;
    
    protected EventOutputWithTimestamp(String kind, long creationDate) {
      super(kind);
      this.creationDate = creationDate;
    }
  }
  
  @SuppressWarnings("unused")
  static final class FeatureRequest extends EventOutputWithTimestamp {
    private final String key;
    private final String userKey;
    private final LDUser user;
    private final Integer version;
    private final Integer variation;
    private final JsonElement value;
    @SerializedName("default") private final JsonElement defaultVal;
    private final String prereqOf;
    private final EvaluationReason reason;
    
    FeatureRequest(long creationDate, String key, String userKey, LDUser user,
        Integer version, Integer variation, JsonElement value, JsonElement defaultVal, String prereqOf,
        EvaluationReason reason, boolean debug) {
      super(debug ? "debug" : "feature", creationDate);
      this.key = key;
      this.userKey = userKey;
      this.user = user;
      this.variation = variation;
      this.version = version;
      this.value = value;
      this.defaultVal = defaultVal;
      this.prereqOf = prereqOf;
      this.reason = reason;
    }
  }

  @SuppressWarnings("unused")
  static final class Identify extends EventOutputWithTimestamp {
    private final LDUser user;
    private final String key;
    
    Identify(long creationDate, LDUser user) {
      super("identify", creationDate);
      this.user = user;
      this.key = user.getKeyAsString();
    }
  }
  
  @SuppressWarnings("unused")
  static final class Custom extends EventOutputWithTimestamp {
    private final String key;
    private final String userKey;
    private final LDUser user;
    private final JsonElement data;
    private final Double metricValue;
    
    Custom(long creationDate, String key, String userKey, LDUser user, JsonElement data, Double metricValue) {
      super("custom", creationDate);
      this.key = key;
      this.userKey = userKey;
      this.user = user;
      this.data = data;
      this.metricValue = metricValue;
    }
  }
  
  @SuppressWarnings("unused")
  static final class Index extends EventOutputWithTimestamp {
    private final LDUser user;
    
    public Index(long creationDate, LDUser user) {
      super("index", creationDate);
      this.user = user;
    }
  }
  
  @SuppressWarnings("unused")
  static final class Summary extends EventOutput {
    private final long startDate;
    private final long endDate;
    private final Map<String, SummaryEventFlag> features;
    
    Summary(long startDate, long endDate, Map<String, SummaryEventFlag> features) {
      super("summary");
      this.startDate = startDate;
      this.endDate = endDate;
      this.features = features;
    }
  }

  static final class SummaryEventFlag {
    @SerializedName("default") final JsonElement defaultVal;
    final List<SummaryEventCounter> counters;
    
    SummaryEventFlag(JsonElement defaultVal, List<SummaryEventCounter> counters) {
      this.defaultVal = defaultVal;
      this.counters = counters;
    }
  }
  
  static final class SummaryEventCounter {
    final Integer variation;
    final JsonElement value;
    final Integer version;
    final long count;
    final Boolean unknown;
    
    SummaryEventCounter(Integer variation, JsonElement value, Integer version, long count, Boolean unknown) {
      this.variation = variation;
      this.value = value;
      this.version = version;
      this.count = count;
      this.unknown = unknown;
    }
  }

  static final class Formatter {
    private final boolean inlineUsers;
    
    Formatter(boolean inlineUsers) {
      this.inlineUsers = inlineUsers;
    }
    
    List<EventOutput> makeOutputEvents(Event[] events, EventSummarizer.EventSummary summary) {
      List<EventOutput> eventsOut = new ArrayList<>(events.length + 1);
      for (Event event: events) {
        eventsOut.add(createOutputEvent(event));
      }
      if (!summary.isEmpty()) {
        eventsOut.add(createSummaryEvent(summary));
      }
      return eventsOut;
    }
    
    private EventOutput createOutputEvent(Event e) {
      String userKey = e.user == null ? null : e.user.getKeyAsString();
      if (e instanceof Event.FeatureRequest) {
        Event.FeatureRequest fe = (Event.FeatureRequest)e;
        boolean inlineThisUser = inlineUsers || fe.debug;
        return new EventOutput.FeatureRequest(fe.creationDate, fe.key,
            inlineThisUser ? null : userKey,
            inlineThisUser ? e.user : null,
            fe.version, fe.variation, fe.value, fe.defaultVal, fe.prereqOf, fe.reason, fe.debug);
      } else if (e instanceof Event.Identify) {
        return new EventOutput.Identify(e.creationDate, e.user);
      } else if (e instanceof Event.Custom) {
        Event.Custom ce = (Event.Custom)e;
        return new EventOutput.Custom(ce.creationDate, ce.key,
            inlineUsers ? null : userKey,
            inlineUsers ? e.user : null,
            ce.data,
            ce.metricValue);
      } else if (e instanceof Event.Index) {
        return new EventOutput.Index(e.creationDate, e.user);
      } else {
        return null;
      }
    }

    private EventOutput createSummaryEvent(EventSummarizer.EventSummary summary) {
      Map<String, SummaryEventFlag> flagsOut = new HashMap<>();
      for (Map.Entry<CounterKey, CounterValue> entry: summary.counters.entrySet()) {
        SummaryEventFlag fsd = flagsOut.get(entry.getKey().key);
        if (fsd == null) {
          fsd = new SummaryEventFlag(entry.getValue().defaultVal, new ArrayList<SummaryEventCounter>());
          flagsOut.put(entry.getKey().key, fsd);
        }
        SummaryEventCounter c = new SummaryEventCounter(
            entry.getKey().variation,
            entry.getValue().flagValue,
            entry.getKey().version,
            entry.getValue().count,
            entry.getKey().version == null ? true : null);
        fsd.counters.add(c);
      }
      return new EventOutput.Summary(summary.startDate, summary.endDate, flagsOut);
    }
  }
}
