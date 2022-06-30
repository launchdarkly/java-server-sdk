package com.launchdarkly.sdk.server;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.EventSummarizer.CounterValue;
import com.launchdarkly.sdk.server.EventSummarizer.FlagInfo;
import com.launchdarkly.sdk.server.EventSummarizer.SimpleIntKeyedMap;
import com.launchdarkly.sdk.server.interfaces.Event;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

/**
 * Transforms analytics events and summary data into the JSON format that we send to LaunchDarkly.
 * Rather than creating intermediate objects to represent this schema, we use the Gson streaming
 * output API to construct JSON directly.
 * 
 * Test coverage for this logic is in EventOutputTest and DefaultEventProcessorOutputTest.
 */
final class EventOutputFormatter {
  private final EventsConfiguration config;
  private final Gson gson;
  
  EventOutputFormatter(EventsConfiguration config) {
    this.config = config;
    this.gson = JsonHelpers.gsonInstanceForEventsSerialization(config);
  }
  
  @SuppressWarnings("resource")
  final int writeOutputEvents(Event[] events, EventSummarizer.EventSummary summary, Writer writer) throws IOException {
    int count = events.length;    
    try (JsonWriter jsonWriter = new JsonWriter(writer)) {
      jsonWriter.beginArray();
      for (Event event: events) {
        writeOutputEvent(event, jsonWriter);
      }
      if (!summary.isEmpty()) {
        writeSummaryEvent(summary, jsonWriter);
        count++;
      }
      jsonWriter.endArray();
    }
    return count;
  }
  
  private final void writeOutputEvent(Event event, JsonWriter jw) throws IOException {
    if (event instanceof Event.FeatureRequest) {
      Event.FeatureRequest fe = (Event.FeatureRequest)event;
      startEvent(fe, fe.isDebug() ? "debug" : "feature", fe.getKey(), jw);
      writeUserOrKey(fe, fe.isDebug(), jw);
      if (fe.getVersion() >= 0) {
        jw.name("version");
        jw.value(fe.getVersion());
      }
      if (fe.getVariation() >= 0) {
        jw.name("variation");
        jw.value(fe.getVariation());
      }
      writeLDValue("value", fe.getValue(), jw);
      writeLDValue("default", fe.getDefaultVal(), jw);
      if (fe.getPrereqOf() != null) {
        jw.name("prereqOf");
        jw.value(fe.getPrereqOf());
      }
      writeEvaluationReason("reason", fe.getReason(), jw);
      if (!fe.getContextKind().equals("user")) {
        jw.name("contextKind").value(fe.getContextKind());
      }
    } else if (event instanceof Event.Identify) {
      startEvent(event, "identify", event.getUser() == null ? null : event.getUser().getKey(), jw);
      writeUser(event.getUser(), jw);
    } else if (event instanceof Event.Custom) {
      Event.Custom ce = (Event.Custom)event;
      startEvent(event, "custom", ce.getKey(), jw);
      writeUserOrKey(ce, false, jw);
      writeLDValue("data", ce.getData(), jw);
      if (!ce.getContextKind().equals("user")) {
        jw.name("contextKind").value(ce.getContextKind());
      }
      if (ce.getMetricValue() != null) {
        jw.name("metricValue");
        jw.value(ce.getMetricValue());
      }
    } else if (event instanceof Event.Index) {
      startEvent(event, "index", null, jw);
      writeUser(event.getUser(), jw);
    } else if (event instanceof Event.AliasEvent) {
      Event.AliasEvent ae = (Event.AliasEvent)event;
      startEvent(event, "alias", ae.getKey(), jw);
      jw.name("contextKind").value(ae.getContextKind());
      jw.name("previousKey").value(ae.getPreviousKey());
      jw.name("previousContextKind").value(ae.getPreviousContextKind());
    } else {
      return;
    }

    jw.endObject();
  }
  
  private final void writeSummaryEvent(EventSummarizer.EventSummary summary, JsonWriter jw) throws IOException {
    jw.beginObject();
    
    jw.name("kind");
    jw.value("summary");
    
    jw.name("startDate");
    jw.value(summary.startDate);
    jw.name("endDate");
    jw.value(summary.endDate);
    
    jw.name("features");
    jw.beginObject();
    
    for (Map.Entry<String, FlagInfo> flag: summary.counters.entrySet()) {
      String flagKey = flag.getKey();
      FlagInfo flagInfo = flag.getValue();
      
      jw.name(flagKey);
      jw.beginObject();
      
      writeLDValue("default", flagInfo.defaultVal, jw);
      
      jw.name("counters");
      jw.beginArray();
      
      for (int i = 0; i < flagInfo.versionsAndVariations.size(); i++) {
        int version = flagInfo.versionsAndVariations.keyAt(i);
        SimpleIntKeyedMap<CounterValue> variations = flagInfo.versionsAndVariations.valueAt(i);
        for (int j = 0; j < variations.size(); j++) {
          int variation = variations.keyAt(j);
          CounterValue counter = variations.valueAt(j);
 
          jw.beginObject();
          
          if (variation >= 0) {
            jw.name("variation").value(variation);
          }
          if (version >= 0) {
            jw.name("version").value(version);
          } else {
            jw.name("unknown").value(true);
          }
          writeLDValue("value", counter.flagValue, jw);
          jw.name("count").value(counter.count);
          
          jw.endObject();
        }
      }

      jw.endArray(); // end of "counters" array
      jw.endObject(); // end of this flag
    }
    
    jw.endObject(); // end of "features"
    jw.endObject(); // end of summary event object
  }
  
  private final void startEvent(Event event, String kind, String key, JsonWriter jw) throws IOException {
    jw.beginObject();
    jw.name("kind");
    jw.value(kind);
    jw.name("creationDate");
    jw.value(event.getCreationDate());
    if (key != null) {
      jw.name("key");
      jw.value(key);
    }
  }
  
  private final void writeUserOrKey(Event event, boolean forceInline, JsonWriter jw) throws IOException {
    LDUser user = event.getUser();
    if (user != null) {
      if (config.inlineUsersInEvents || forceInline) {
        writeUser(user, jw);
      } else {
        jw.name("userKey");
        jw.value(user.getKey());
      }
    }
  }
  
  private final void writeUser(LDUser user, JsonWriter jw) throws IOException {
    jw.name("user");
    // config.gson is already set up to use our custom serializer, which knows about private attributes
    // and already uses the streaming approach
    gson.toJson(user, LDUser.class, jw);
  }
  
  private final void writeLDValue(String key, LDValue value, JsonWriter jw) throws IOException {
    if (value == null || value.isNull()) {
      return;
    }
    jw.name(key);
    gson.toJson(value, LDValue.class, jw); // LDValue defines its own custom serializer
  }
  
  // This logic is so that we don't have to define multiple custom serializers for the various reason subclasses.
  private final void writeEvaluationReason(String key, EvaluationReason er, JsonWriter jw) throws IOException {
    if (er == null) {
      return;
    }
    jw.name(key);
    gson.toJson(er, EvaluationReason.class, jw); // EvaluationReason defines its own custom serializer
  }
}
