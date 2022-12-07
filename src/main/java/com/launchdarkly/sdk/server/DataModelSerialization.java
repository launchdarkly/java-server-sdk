package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Rollout;
import com.launchdarkly.sdk.server.DataModel.RolloutKind;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.SegmentRule;
import com.launchdarkly.sdk.server.DataModel.VersionedData;
import com.launchdarkly.sdk.server.DataModel.WeightedVariation;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.subsystems.SerializationException;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.JsonHelpers.gsonInstance;
import static com.launchdarkly.sdk.server.JsonHelpers.gsonInstanceWithNullsAllowed;

/**
 * JSON conversion logic specifically for our data model types.
 * <p>
 * More general JSON helpers are in JsonHelpers.
 */
abstract class DataModelSerialization {
  /**
   * Deserializes a data model object from JSON that was already parsed by Gson.
   * <p>
   * For built-in data model classes, our usual abstraction for deserializing from a string is inefficient in
   * this case, because Gson has already parsed the original JSON and then we would have to convert the
   * JsonElement back into a string and parse it again. So it's best to call Gson directly instead of going
   * through our abstraction in that case, but it's also best to implement that special-casing just once here
   * instead of scattered throughout the SDK.
   * 
   * @param kind the data kind
   * @param parsedJson the parsed JSON
   * @return the deserialized item
   */
  static VersionedData deserializeFromParsedJson(DataKind kind, JsonElement parsedJson) throws SerializationException {
    VersionedData item;
    try {
      if (kind == FEATURES) {
        item = gsonInstance().fromJson(parsedJson, FeatureFlag.class);
      } else if (kind == SEGMENTS) {
        item = gsonInstance().fromJson(parsedJson, Segment.class);
      } else {
        // This shouldn't happen since we only use this method internally with our predefined data kinds
        throw new IllegalArgumentException("unknown data kind");
      }
    } catch (RuntimeException e) {
      // A variety of unchecked exceptions can be thrown from JSON parsing; treat them all the same
      throw new SerializationException(e);
    }
    return item;
  }

  /**
   * Deserializes a data model object from a Gson reader.
   * 
   * @param kind the data kind
   * @param jr the JSON reader
   * @return the deserialized item
   */
  static VersionedData deserializeFromJsonReader(DataKind kind, JsonReader jr) throws SerializationException {
    VersionedData item;
    try {
      if (kind == FEATURES) {
        item = gsonInstance().fromJson(jr, FeatureFlag.class);
      } else if (kind == SEGMENTS) {
        item = gsonInstance().fromJson(jr, Segment.class);
      } else {
        // This shouldn't happen since we only use this method internally with our predefined data kinds
        throw new IllegalArgumentException("unknown data kind");
      }
    } catch (RuntimeException e) {
      // A variety of unchecked exceptions can be thrown from JSON parsing; treat them all the same
      throw new SerializationException(e);
    }
    return item;
  }

  /**
   * Deserializes a full set of flag/segment data from a standard JSON object representation
   * in the form {"flags": ..., "segments": ...} (which is used in both streaming and polling
   * responses).
   * 
   * @param jr the JSON reader
   * @return the deserialized data
   */
  static FullDataSet<ItemDescriptor> parseFullDataSet(JsonReader jr) throws SerializationException {
    ImmutableList.Builder<Map.Entry<String, ItemDescriptor>> flags = ImmutableList.builder();
    ImmutableList.Builder<Map.Entry<String, ItemDescriptor>> segments = ImmutableList.builder();
    
    try {
      jr.beginObject();
      while (jr.peek() != JsonToken.END_OBJECT) {
        String kindName = jr.nextName();
        Class<?> itemClass;
        ImmutableList.Builder<Map.Entry<String, ItemDescriptor>> listBuilder;
        switch (kindName) {
        case "flags":
          itemClass = DataModel.FeatureFlag.class;
          listBuilder = flags;
          break;
        case "segments":
          itemClass = DataModel.Segment.class;
          listBuilder = segments;
          break;
        default:
          jr.skipValue();
          continue;
        }
        jr.beginObject();
        while (jr.peek() != JsonToken.END_OBJECT) {
          String key = jr.nextName();
          @SuppressWarnings("unchecked")
          Object item = JsonHelpers.deserialize(jr, (Class<Object>)itemClass);
          listBuilder.add(new AbstractMap.SimpleEntry<>(key,
              new ItemDescriptor(((VersionedData)item).getVersion(), item)));
        }
        jr.endObject();
      }
      jr.endObject();

      return new FullDataSet<ItemDescriptor>(ImmutableMap.of(
          FEATURES, new KeyedItems<>(flags.build()),
          SEGMENTS, new KeyedItems<>(segments.build())
          ).entrySet());
    } catch (IOException e) {
      throw new SerializationException(e);
    } catch (RuntimeException e) {
      // A variety of unchecked exceptions can be thrown from JSON parsing; treat them all the same
      throw new SerializationException(e);
    }
  }
  
  // Custom deserialization logic for Clause because the attribute field is treated differently
  // depending on the contextKind field (if contextKind is null, we always parse attribute as a
  // literal attribute name and not a reference).
  static class ClauseTypeAdapter extends TypeAdapter<Clause> {
    @Override
    public void write(JsonWriter out, Clause c) throws IOException {
      out.beginObject();
      if (c.getContextKind() != null) {
        out.name("contextKind").value(c.getContextKind().toString());
      }
      out.name("attribute").value(c.getAttribute() == null ? null : c.getAttribute().toString());
      out.name("op").value(c.getOp() == null ? null : c.getOp().name());
      out.name("values").beginArray();
      for (LDValue v: c.getValues()) {
        gsonInstanceWithNullsAllowed().toJson(v, LDValue.class, out);
      }
      out.endArray();
      out.name("negate").value(c.isNegate());
      out.endObject();
    }

    @Override
    public Clause read(JsonReader in) throws IOException {
      ContextKind contextKind = null;
      String attrString = null;
      Operator op = null;
      List<LDValue> values = new ArrayList<>();
      boolean negate = false;
      in.beginObject();
      while (in.hasNext()) {
        switch (in.nextName()) {
        case "contextKind":
          contextKind = ContextKind.of(in.nextString());
          break;
        case "attribute":
          attrString = in.nextString();
          break;
        case "op":
          op = Operator.forName(in.nextString());
          break;
        case "values":
          if (in.peek() == JsonToken.NULL) {
            in.skipValue();
          } else {
            in.beginArray();
            while (in.hasNext()) {
              LDValue value = gsonInstanceWithNullsAllowed().fromJson(in, LDValue.class);
              values.add(value);
            }
            in.endArray();
          }
          break;
        case "negate":
          negate = in.nextBoolean();
          break;
        default:
           in.skipValue();
        }
      }
      in.endObject();
      AttributeRef attribute = attributeNameOrPath(attrString, contextKind);
      return new Clause(contextKind, attribute, op, values, negate);
    }
  }

  // Custom deserialization logic for Rollout for a similar reason to Clause.
  static class RolloutTypeAdapter extends TypeAdapter<Rollout> {
    @Override
    public void write(JsonWriter out, Rollout r) throws IOException {
      out.beginObject();
      if (r.getContextKind() != null) {
        out.name("contextKind").value(r.getContextKind().toString());
      }
      out.name("variations").beginArray();
      for (WeightedVariation wv: r.getVariations()) {
        gsonInstanceWithNullsAllowed().toJson(wv, WeightedVariation.class, out);
      }
      out.endArray();
      if (r.getBucketBy() != null) {
        out.name("bucketBy").value(r.getBucketBy().toString());
      }
      if (r.getKind() != RolloutKind.rollout) {
        out.name("kind").value(r.getKind().name());
      }
      if (r.getSeed() != null) {
        out.name("seed").value(r.getSeed());
      }
      out.endObject();
    }

    @Override
    public Rollout read(JsonReader in) throws IOException {
      ContextKind contextKind = null;
      List<WeightedVariation> variations = new ArrayList<>();
      String bucketByString = null;
      RolloutKind kind = RolloutKind.rollout;
      Integer seed = null;
      in.beginObject();
      while (in.hasNext()) {
        switch (in.nextName()) {
        case "contextKind":
          contextKind = ContextKind.of(in.nextString());
          break;
        case "variations":
          if (in.peek() == JsonToken.NULL) {
            in.skipValue();
          } else {
            in.beginArray();
            while (in.hasNext()) {
              WeightedVariation wv = gsonInstanceWithNullsAllowed().fromJson(in, WeightedVariation.class);
              variations.add(wv);
            }
            in.endArray();
          }
          break;
        case "bucketBy":
          bucketByString = in.nextString();
          break;
        case "kind":
          kind = RolloutKind.experiment.name().equals(in.nextString()) ? RolloutKind.experiment :
            RolloutKind.rollout;
          break;
        case "seed":
          seed = readNullableInt(in);
          break;
        default:
           in.skipValue();
        }
      }
      in.endObject();
      AttributeRef bucketBy = attributeNameOrPath(bucketByString, contextKind);
      return new Rollout(contextKind, variations, bucketBy, kind, seed);
    }
  }

  // Custom deserialization logic for SegmentRule for a similar reason to Clause.
  static class SegmentRuleTypeAdapter extends TypeAdapter<SegmentRule> {
    @Override
    public void write(JsonWriter out, SegmentRule sr) throws IOException {
      out.beginObject();
      out.name("clauses").beginArray();
      for (Clause c: sr.getClauses()) {
        gsonInstanceWithNullsAllowed().toJson(c, Clause.class, out);
      }
      out.endArray();
      if (sr.getWeight() != null) {
        out.name("weight").value(sr.getWeight());
      }
      if (sr.getRolloutContextKind() != null) {
        out.name("rolloutContextKind").value(sr.getRolloutContextKind().toString());
      }
      if (sr.getBucketBy() != null) {
        out.name("bucketBy").value(sr.getBucketBy().toString());
      }
      out.endObject();
    }

    @Override
    public SegmentRule read(JsonReader in) throws IOException {
      List<Clause> clauses = new ArrayList<>();
      Integer weight = null;
      ContextKind rolloutContextKind = null;
      String bucketByString = null;
      in.beginObject();
      while (in.hasNext()) {
        switch (in.nextName()) {
        case "clauses":
          if (in.peek() == JsonToken.NULL) {
            in.skipValue();
          } else {
            in.beginArray();
            while (in.hasNext()) {
              Clause c = gsonInstanceWithNullsAllowed().fromJson(in, Clause.class);
              clauses.add(c);
            }
            in.endArray();
          }
          break;
        case "weight":
          weight = readNullableInt(in);
          break;
        case "rolloutContextKind":
          rolloutContextKind = ContextKind.of(in.nextString());
          break;
        case "bucketBy":
          bucketByString = in.nextString();
          break;
        default:
           in.skipValue();
        }
      }
      in.endObject();
      AttributeRef bucketBy = attributeNameOrPath(bucketByString, rolloutContextKind);
      return new SegmentRule(clauses, weight, rolloutContextKind, bucketBy);
    }
  }
  
  static Integer readNullableInt(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.skipValue();
      return null;
    }
    return in.nextInt();
  }
  
  static AttributeRef attributeNameOrPath(String attrString, ContextKind contextKind) {
    if (attrString == null) {
      return null;
    }
    return contextKind == null ? AttributeRef.fromLiteral(attrString) : AttributeRef.fromPath(attrString);
  }
}
