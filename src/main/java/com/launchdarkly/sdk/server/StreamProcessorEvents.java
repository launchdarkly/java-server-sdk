package com.launchdarkly.sdk.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.launchdarkly.sdk.server.DataModel.VersionedData;
import com.launchdarkly.sdk.server.DataModelDependencies.KindAndKey;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.SerializationException;

import java.io.IOException;

import static com.launchdarkly.sdk.server.DataModel.ALL_DATA_KINDS;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.DataModelSerialization.deserializeFromJsonReader;
import static com.launchdarkly.sdk.server.DataModelSerialization.deserializeFromParsedJson;
import static com.launchdarkly.sdk.server.DataModelSerialization.parseFullDataSet;
import static com.launchdarkly.sdk.server.JsonHelpers.gsonInstance;

// Deserialization of stream message data is all encapsulated here, so StreamProcessor can
// deal with just the logical behavior of the stream and we can test this logic separately.
// The parseFullDataSet
//
// All of the parsing methods have the following behavior:
//
// - They take the input data as a JsonReader, which the caller is responsible for constructing.
//
// - A SerializationException is thrown for any malformed data. That includes 1. totally invalid
// JSON, 2. well-formed JSON that is missing a necessary property for this message type. The Gson
// parser can throw various kinds of unchecked exceptions for things like wrong data types, but we
// wrap them all in SerializationException.
//
// - For messages that have a "path" property, which might be for instance "/flags/xyz" to refer
// to a feature flag with the key "xyz", an unrecognized path like "/cats/Lucy" is not considered
// an error since it might mean LaunchDarkly now supports some new kind of data the SDK can't yet
// use and should ignore. In this case we simply return null in place of a DataKind.
abstract class StreamProcessorEvents {
  // This is the logical representation of the data in the "put" event. In the JSON representation,
  // the "data" property is actually a map of maps, but the schema we use internally is a list of
  // lists instead.
  //
  // The "path" property is normally always "/"; the LD streaming service sends this property, but
  // some versions of Relay do not, so we do not require it.
  //
  // Example JSON representation:
  //
  // {
  //   "path": "/",
  //   "data": {
  //     "flags": {
  //       "flag1": { "key": "flag1", "version": 1, ...etc. },
  //       "flag2": { "key": "flag2", "version": 1, ...etc. },
  //     },
  //     "segments": {
  //       "segment1": { "key", "segment1", "version": 1, ...etc. }
  //     }
  //   }
  // }
  static final class PutData {
    final String path;
    final FullDataSet<ItemDescriptor> data;
    
    PutData(String path, FullDataSet<ItemDescriptor> data) {
      this.path = path;
      this.data = data;
    }
  }

  // This is the logical representation of the data in the "patch" event. In the JSON representation,
  // there is a "path" property in the format "/flags/key" or "/segments/key", which we convert into
  // Kind and Key when we parse it. The "data" property is the JSON representation of the flag or
  // segment, which we deserialize into an ItemDescriptor.
  //
  // Example JSON representation:
  //
  // {
  //   "path": "/flags/flagkey",
  //   "data": {
  //     "key": "flagkey",
  //     "version": 2, ...etc.
  //   }
  // }
  static final class PatchData {
    final DataKind kind;
    final String key;
    final ItemDescriptor item;

    PatchData(DataKind kind, String key, ItemDescriptor item) {
      this.kind = kind;
      this.key = key;
      this.item = item;
    }
  }

  // This is the logical representation of the data in the "delete" event. In the JSON representation,
  // there is a "path" property in the format "/flags/key" or "/segments/key", which we convert into
  // Kind and Key when we parse it.
  //
  // Example JSON representation:
  //
  // {
  //   "path": "/flags/flagkey",
  //   "version": 3
  // }
  static final class DeleteData {
    final DataKind kind;
    final String key;
    final int version;

    public DeleteData(DataKind kind, String key, int version) {
      this.kind = kind;
      this.key = key;
      this.version = version;
    }
  }
  
  static PutData parsePutData(JsonReader jr) {
    String path = null;
    FullDataSet<ItemDescriptor> data = null;
    
    try {
      jr.beginObject();
      while (jr.peek() != JsonToken.END_OBJECT) {
        String prop = jr.nextName();
        switch (prop) {
        case "path":
          path = jr.nextString();
          break;
        case "data":
          data = parseFullDataSet(jr);
          break;
        default:
          jr.skipValue(); 
        }
      }
      jr.endObject();
      
      if (data == null) {
        throw missingRequiredProperty("put", "data");
      }
      
      return new PutData(path, data);
    } catch (IOException e) {
      throw new SerializationException(e);
    } catch (RuntimeException e) {
      throw new SerializationException(e);
    }
  }
  
  static PatchData parsePatchData(JsonReader jr) {
    // The logic here is a little convoluted because JSON object property ordering is arbitrary, so
    // we don't know for sure that we'll see the "path" property before the "data" property, but we
    // won't know what kind of object to parse "data" into until we know whether "path" starts with
    // "/flags" or "/segments". So, if we see "data" first, we'll have to pull its value into a
    // temporary buffer and parse it afterward, which is less efficient than parsing directly from
    // the stream. However, in practice, the LD streaming service does send "path" first.
    DataKind kind = null;
    String key = null;
    VersionedData dataItem = null;
    JsonElement bufferedParsedData = null;

    try {
      jr.beginObject();
      while (jr.peek() != JsonToken.END_OBJECT) {
        String prop = jr.nextName();
        switch (prop) {
        case "path":
          KindAndKey kindAndKey = parsePath(jr.nextString());
          if (kindAndKey == null) {
            // An unrecognized path isn't considered an error; we'll just return a null kind,
            // indicating that we should ignore this event.
            return new PatchData(null, null, null);
          }
          kind = kindAndKey.kind;
          key = kindAndKey.key;
          break;
        case "data":
          if (kind != null) {
            dataItem = deserializeFromJsonReader(kind, jr);
          } else {
            bufferedParsedData = gsonInstance().fromJson(jr, JsonElement.class);
          }
          break;
        default:
          jr.skipValue();
        }
      }
      jr.endObject();
      
      if (kind == null) {
        throw missingRequiredProperty("patch", "path");
      }
      if (dataItem == null) {
        if (bufferedParsedData == null) {
          throw missingRequiredProperty("patch", "path");
        }
        dataItem = deserializeFromParsedJson(kind, bufferedParsedData);
      }
      return new PatchData(kind, key, new ItemDescriptor(dataItem.getVersion(), dataItem));
    } catch (IOException e) {
      throw new SerializationException(e);
    } catch (RuntimeException e) {
      throw new SerializationException(e);
    }
  }
  
  static DeleteData parseDeleteData(JsonReader jr) {
    DataKind kind = null;
    String key = null;
    Integer version = null;
    
    try {
      jr.beginObject();
      while (jr.peek() != JsonToken.END_OBJECT) {
        String prop = jr.nextName();
        switch (prop) {
        case "path":
          KindAndKey kindAndKey = parsePath(jr.nextString());
          if (kindAndKey == null) {
            // An unrecognized path isn't considered an error; we'll just return a null kind,
            // indicating that we should ignore this event.
            return new DeleteData(null, null, 0);
          }
          kind = kindAndKey.kind;
          key = kindAndKey.key;
          break;
        case "version":
          version = jr.nextInt();
          break;
        default:
          jr.skipValue();
        }
      }
      jr.endObject();
      
      if (kind == null) {
        throw missingRequiredProperty("delete", "path");
      }
      if (version == null) {
        throw missingRequiredProperty("delete", "version");
      }
      return new DeleteData(kind, key, version);
    } catch (IOException e) {
      throw new SerializationException(e);
    } catch (RuntimeException e) {
      throw new SerializationException(e);
    }
  }
  
  private static KindAndKey parsePath(String path) {
    if (path == null) {
      throw new JsonParseException("item path cannot be null");
    }
    for (DataKind kind: ALL_DATA_KINDS) {
      String prefix = (kind == SEGMENTS) ? "/segments/" : "/flags/";
      if (path.startsWith(prefix)) {
        return new KindAndKey(kind, path.substring(prefix.length()));
      }
    }
    return null; // we don't recognize the path - the caller should ignore this event, just as we ignore unknown event types
  }
  
  private static JsonParseException missingRequiredProperty(String eventName, String propName) {
    return new JsonParseException(String.format("stream \"{}\" event did not have required property \"{}\"",
        eventName, propName));
  }
}
