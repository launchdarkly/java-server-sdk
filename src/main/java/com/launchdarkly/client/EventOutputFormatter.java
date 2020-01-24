package com.launchdarkly.client;

import com.google.gson.stream.JsonWriter;
import com.launchdarkly.client.EventSummarizer.CounterKey;
import com.launchdarkly.client.EventSummarizer.CounterValue;
import com.launchdarkly.client.value.LDValue;

import java.io.IOException;
import java.io.Writer;

/**
 * Transforms analytics events and summary data into the JSON format that we send to LaunchDarkly.
 * Rather than creating intermediate objects to represent this schema, we use the Gson streaming
 * output API to construct JSON directly.
 */
final class EventOutputFormatter {
  private final LDConfig config;
  
  EventOutputFormatter(LDConfig config) {
    this.config = config;
  }
  
  int writeOutputEvents(Event[] events, EventSummarizer.EventSummary summary, Writer writer) throws IOException {
    int count = 0;    
    try (JsonWriter jsonWriter = new JsonWriter(writer)) {
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
    }
    return count;
  }
  
  private boolean writeOutputEvent(Event event, JsonWriter jw) throws IOException {
    if (event instanceof Event.FeatureRequest) {
      Event.FeatureRequest fe = (Event.FeatureRequest)event;
      startEvent(fe, fe.debug ? "debug" : "feature", fe.key, jw);
      writeUserOrKey(fe, fe.debug, jw);
      if (fe.version != null) {
        jw.name("version");
        jw.value(fe.version);
      }
      if (fe.variation != null) {
        jw.name("variation");
        jw.value(fe.variation);
      }
      writeLDValue("value", fe.value, jw);
      writeLDValue("default", fe.defaultVal, jw);
      if (fe.prereqOf != null) {
        jw.name("prereqOf");
        jw.value(fe.prereqOf);
      }
      writeEvaluationReason("reason", fe.reason, jw);
      jw.endObject();
    } else if (event instanceof Event.Identify) {
      startEvent(event, "identify", event.user == null ? null : event.user.getKeyAsString(), jw);
      writeUser(event.user, jw);
      jw.endObject();
    } else if (event instanceof Event.Custom) {
      Event.Custom ce = (Event.Custom)event;
      startEvent(event, "custom", ce.key, jw);
      writeUserOrKey(ce, false, jw);
      writeLDValue("data", ce.data, jw);
      if (ce.metricValue != null) {
        jw.name("metricValue");
        jw.value(ce.metricValue);
      }
      jw.endObject();
    } else if (event instanceof Event.Index) {
      startEvent(event, "index", null, jw);
      writeUser(event.user, jw);
      jw.endObject();
    } else {
      return false;
    }
    return true;
  }
  
  private void writeSummaryEvent(EventSummarizer.EventSummary summary, JsonWriter jw) throws IOException {
    jw.beginObject();
    
    jw.name("kind");
    jw.value("summary");
    
    jw.name("startDate");
    jw.value(summary.startDate);
    jw.name("endDate");
    jw.value(summary.endDate);
    
    jw.name("features");
    jw.beginObject();
    
    CounterKey[] unprocessedKeys = summary.counters.keySet().toArray(new CounterKey[summary.counters.size()]);
    for (int i = 0; i < unprocessedKeys.length; i++) {
      if (unprocessedKeys[i] == null) {
        continue;
      }
      CounterKey key = unprocessedKeys[i];
      String flagKey = key.key;
      CounterValue firstValue = summary.counters.get(key);
      
      jw.name(flagKey);
      jw.beginObject();
      
      writeLDValue("default", firstValue.defaultVal, jw);
      
      jw.name("counters");
      jw.beginArray();
      
      for (int j = i; j < unprocessedKeys.length; j++) {
        CounterKey keyForThisFlag = unprocessedKeys[j];
        if (j != i && (keyForThisFlag == null || !keyForThisFlag.key.equals(flagKey))) {
          continue;
        }
        CounterValue value = keyForThisFlag == key ? firstValue : summary.counters.get(keyForThisFlag);
        unprocessedKeys[j] = null;
           
        jw.beginObject();
        
        if (keyForThisFlag.variation != null) {
          jw.name("variation");
          jw.value(keyForThisFlag.variation);
        }
        if (keyForThisFlag.version != null) {
          jw.name("version");
          jw.value(keyForThisFlag.version);
        } else {
          jw.name("unknown");
          jw.value(true);
        }
        writeLDValue("value", value.flagValue, jw);
        jw.name("count");
        jw.value(value.count);
        
        jw.endObject(); // end of this counter
      }
      
      jw.endArray(); // end of "counters" array
      
      jw.endObject(); // end of this flag
    }
    
    jw.endObject(); // end of "features"
    
    jw.endObject();
  }
  
  private void startEvent(Event event, String kind, String key, JsonWriter jw) throws IOException {
    jw.beginObject();
    jw.name("kind");
    jw.value(kind);
    jw.name("creationDate");
    jw.value(event.creationDate);
    if (key != null) {
      jw.name("key");
      jw.value(key);
    }
  }
  
  private void writeUserOrKey(Event event, boolean forceInline, JsonWriter jw) throws IOException {
    LDUser user = event.user;
    if (user != null) {
      if (config.inlineUsersInEvents || forceInline) {
        writeUser(user, jw);
      } else {
        jw.name("userKey");
        jw.value(user.getKeyAsString());
      }
    }
  }
  
  private void writeUser(LDUser user, JsonWriter jw) throws IOException {
    jw.name("user");
    // config.gson is already set up to use our custom serializer, which knows about private attributes
    // and already uses the streaming approach
    config.gson.toJson(user, LDUser.class, jw);
  }
  
  private void writeLDValue(String key, LDValue value, JsonWriter jw) throws IOException {
    if (value == null || value.isNull()) {
      return;
    }
    jw.name(key);
    config.gson.toJson(value, LDValue.class, jw); // LDValue defines its own custom serializer
  }
  
  // This logic is so that we don't have to define multiple custom serializers for the various reason subclasses.
  private void writeEvaluationReason(String key, EvaluationReason er, JsonWriter jw) throws IOException {
    if (er == null) {
      return;
    }
    jw.name(key);
    
    jw.beginObject();
    
    jw.name("kind");
    jw.value(er.getKind().name());
    
    if (er instanceof EvaluationReason.Error) {
      EvaluationReason.Error ere = (EvaluationReason.Error)er;
      jw.name("errorKind");
      jw.value(ere.getErrorKind().name());
    } else if (er instanceof EvaluationReason.PrerequisiteFailed) {
      EvaluationReason.PrerequisiteFailed erpf = (EvaluationReason.PrerequisiteFailed)er;
      jw.name("prerequisiteKey");
      jw.value(erpf.getPrerequisiteKey());
    } else if (er instanceof EvaluationReason.RuleMatch) {
      EvaluationReason.RuleMatch errm = (EvaluationReason.RuleMatch)er;
      jw.name("ruleIndex");
      jw.value(errm.getRuleIndex());
      if (errm.getRuleId() != null) {
        jw.name("ruleId");
        jw.value(errm.getRuleId());
      }
    }   
    
    jw.endObject();
  }
}
