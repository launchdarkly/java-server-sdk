package com.launchdarkly.sdk.internal.events;

import com.google.gson.stream.JsonWriter;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.internal.events.EventSummarizer.CounterValue;
import com.launchdarkly.sdk.internal.events.EventSummarizer.FlagInfo;
import com.launchdarkly.sdk.internal.events.EventSummarizer.SimpleIntKeyedMap;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import static com.launchdarkly.sdk.internal.GsonHelpers.gsonInstance;

/**
 * Transforms analytics events and summary data into the JSON format that we send to LaunchDarkly.
 * Rather than creating intermediate objects to represent this schema, we use the Gson streaming
 * output API to construct JSON directly.
 * 
 * Test coverage for this logic is in EventOutputTest and DefaultEventProcessorOutputTest. The
 * handling of context data and private attribute redaction is implemented in EventContextFormatter
 * and tested in more detail in EventContextFormatterTest. 
 */
final class EventOutputFormatter {
  private final EventContextFormatter contextFormatter;
  
  EventOutputFormatter(EventsConfiguration config) {
    this.contextFormatter = new EventContextFormatter(
        config.allAttributesPrivate,
        config.privateAttributes.toArray(new AttributeRef[config.privateAttributes.size()]));
  }
  
  final int writeOutputEvents(Event[] events, EventSummarizer.EventSummary summary, Writer writer) throws IOException {
    int count = 0;    
    JsonWriter jsonWriter = new JsonWriter(writer);
    jsonWriter.beginArray();
    for (Event event: events) {
      if (writeOutputEvent(event, jsonWriter)) {
        count++;
      }
    }
    if (!summary.isEmpty()) {
      writeSummaryEvent(summary, jsonWriter);
      count++;
    }
    jsonWriter.endArray();
    jsonWriter.flush();
    return count;
  }
  
  private final boolean writeOutputEvent(Event event, JsonWriter jw) throws IOException {
    if (event.getContext() == null || !event.getContext().isValid()) {
      // The SDK should never send us an event without a valid context, but if we somehow get one,
      // just skip the event since there's no way to serialize it.
      return false;
    }
    if (event instanceof Event.FeatureRequest) {
      Event.FeatureRequest fe = (Event.FeatureRequest)event;
      jw.beginObject();
      writeKindAndCreationDate(jw, fe.isDebug() ? "debug" : "feature", event.getCreationDate());
      jw.name("key").value(fe.getKey());
      if (fe.isDebug()) {
        writeContext(fe.getContext(), jw);
      } else {
        writeContextKeys(fe.getContext(), jw);
      }
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
      jw.endObject();
    } else if (event instanceof Event.Identify) {
      jw.beginObject();
      writeKindAndCreationDate(jw, "identify", event.getCreationDate());
      writeContext(event.getContext(), jw);
      jw.endObject();
    } else if (event instanceof Event.Custom) {
      Event.Custom ce = (Event.Custom)event;
      jw.beginObject();
      writeKindAndCreationDate(jw, "custom", event.getCreationDate());
      jw.name("key").value(ce.getKey());
      writeContextKeys(ce.getContext(), jw);
      writeLDValue("data", ce.getData(), jw);
      if (ce.getMetricValue() != null) {
        jw.name("metricValue");
        jw.value(ce.getMetricValue());
      }
      jw.endObject();
    } else if (event instanceof Event.Index) {
      jw.beginObject();
      writeKindAndCreationDate(jw, "index", event.getCreationDate());
      writeContext(event.getContext(), jw);
      jw.endObject();
    } else {
      return false;
    }
    return true;
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
      jw.name("contextKinds").beginArray();
      for (String kind: flagInfo.contextKinds) {
        jw.value(kind);
      }
      jw.endArray();
      
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
  
  private final void writeKindAndCreationDate(JsonWriter jw, String kind, long creationDate) throws IOException {
    jw.name("kind").value(kind);
    jw.name("creationDate").value(creationDate);
  }
  
  private final void writeContext(LDContext context, JsonWriter jw) throws IOException {
    jw.name("context");
    contextFormatter.write(context, jw);
  }
  
  private final void writeContextKeys(LDContext context, JsonWriter jw) throws IOException {
    jw.name("contextKeys").beginObject();
    for (int i = 0; i < context.getIndividualContextCount(); i++) {
      LDContext c = context.getIndividualContext(i);
      if (c != null) {
        jw.name(c.getKind().toString()).value(c.getKey());
      }
    }
    jw.endObject();
  }
  
  private final void writeLDValue(String key, LDValue value, JsonWriter jw) throws IOException {
    if (value == null || value.isNull()) {
      return;
    }
    jw.name(key);
    gsonInstance().toJson(value, LDValue.class, jw); // LDValue defines its own custom serializer
  }
  
  private final void writeEvaluationReason(String key, EvaluationReason er, JsonWriter jw) throws IOException {
    if (er == null) {
      return;
    }
    jw.name(key);
    gsonInstance().toJson(er, EvaluationReason.class, jw); // EvaluationReason defines its own custom serializer
  }
}
