package com.launchdarkly.sdk.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.VersionedData;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.SerializationException;

import java.io.IOException;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;

abstract class JsonHelpers {
  private static final Gson gson = new Gson();
  
  /**
   * Returns a shared instance of Gson with default configuration. This should not be used for serializing
   * event data, since it does not have any of the configurable behavior related to private attributes.
   * Code in _unit tests_ should _not_ use this method, because the tests can be run from other projects
   * in an environment where the classpath contains a shaded copy of Gson instead of regular Gson.
   */
  static Gson gsonInstance() {
    return gson;
  }
  
  /**
   * Creates a Gson instance that will correctly serialize users for the given configuration (private attributes, etc.).
   */
  static Gson gsonInstanceForEventsSerialization(EventsConfiguration config) {
    return new GsonBuilder()
        .registerTypeAdapter(LDUser.class, new EventUserSerialization.UserAdapterWithPrivateAttributeBehavior(config))
        .create();    
  }

  /**
   * Deserializes an object from JSON. We should use this helper method instead of directly calling
   * gson.fromJson() to minimize reliance on details of the framework we're using, and to ensure that we
   * consistently use our wrapper exception.
   * 
   * @param json the serialized JSON string
   * @param objectClass class of object to create
   * @return the deserialized object
   * @throws SerializationException if Gson throws an exception
   */
  static <T> T deserialize(String json, Class<T> objectClass) throws SerializationException {
    try {
      return gson.fromJson(json, objectClass);
    } catch (Exception e) {
      throw new SerializationException(e);
    }
  }
  
  /**
   * Serializes an object to JSON. We should use this helper method instead of directly calling
   * gson.toJson() to minimize reliance on details of the framework we're using (except when we need to use
   * gsonInstanceForEventsSerialization, since our event serialization logic isn't well suited to using a
   * simple abstraction).
   * 
   * @param o the object to serialize
   * @return the serialized JSON string
   */
  static String serialize(Object o) {
    return gson.toJson(o);
  }
  
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
        item = gson.fromJson(parsedJson, FeatureFlag.class);
      } else if (kind == SEGMENTS) {
        item = gson.fromJson(parsedJson, Segment.class);
      } else {
        // This shouldn't happen since we only use this method internally with our predefined data kinds
        throw new IllegalArgumentException("unknown data kind");
      }
    } catch (JsonParseException e) {
      throw new SerializationException(e);
    }
    return item;
  }

  /**
   * Implement this interface on any internal class that needs to do some kind of post-processing after
   * being unmarshaled from JSON. You must also add the annotation {@code JsonAdapter(JsonHelpers.PostProcessingDeserializableTypeAdapterFactory)}
   * to the class for this to work.
   */
  static interface PostProcessingDeserializable {
    void afterDeserialized();
  }
  
  static class PostProcessingDeserializableTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      return new PostProcessingDeserializableTypeAdapter<>(gson.getDelegateAdapter(this, type));
    }
  }
  
  private static class PostProcessingDeserializableTypeAdapter<T> extends TypeAdapter<T> {
    private final TypeAdapter<T> baseAdapter;
    
    PostProcessingDeserializableTypeAdapter(TypeAdapter<T> baseAdapter) {
      this.baseAdapter = baseAdapter;
    }
    
    @Override
    public void write(JsonWriter out, T value) throws IOException {
      baseAdapter.write(out, value);
    }

    @Override
    public T read(JsonReader in) throws IOException {
      T instance = baseAdapter.read(in);
      if (instance instanceof PostProcessingDeserializable) {
        ((PostProcessingDeserializable)instance).afterDeserialized();
      }
      return instance;
    }
  }
}
