package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.VersionedData;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.interfaces.SerializationException;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.JsonHelpers.gsonInstance;

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
}
