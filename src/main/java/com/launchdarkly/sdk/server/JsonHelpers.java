package com.launchdarkly.sdk.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.launchdarkly.sdk.server.subsystems.SerializationException;

import java.io.IOException;

abstract class JsonHelpers {
  private JsonHelpers() {}
  
  private static final Gson gsonWithNullsAllowed = new GsonBuilder().serializeNulls().create();
  private static final Gson gsonWithNullsSuppressed = new GsonBuilder().create();
  
  /**
   * Returns a shared instance of Gson with default configuration. This should not be used for serializing
   * event data, since it does not have any of the configurable behavior related to private attributes.
   * Code in _unit tests_ should _not_ use this method, because the tests can be run from other projects
   * in an environment where the classpath contains a shaded copy of Gson instead of regular Gson.
   * 
   * @see #gsonWithNullsAllowed
   */
  static Gson gsonInstance() {
    return gsonWithNullsSuppressed;
  }

  /**
   * Returns a shared instance of Gson with default configuration except that properties with null values
   * are <i>not</i> automatically dropped. We use this in contexts where we want to exactly reproduce
   * whatever the serializer for a type is outputting.
   * 
   * @see #gsonInstance()
   */
  static Gson gsonInstanceWithNullsAllowed() {
    return gsonWithNullsAllowed;
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
      return gsonInstance().fromJson(json, objectClass);
    } catch (Exception e) {
      throw new SerializationException(e);
    }
  }
  
  /**
   * Deserializes an object from a JSON stream.
   * 
   * @param reader the JSON reader
   * @param objectClass class of object to create
   * @return the deserialized object
   * @throws SerializationException if Gson throws an exception
   */
  static <T> T deserialize(JsonReader reader, Class<T> objectClass) throws SerializationException {
    try {
      return gsonInstance().fromJson(reader, objectClass);
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
    return gsonInstance().toJson(o);
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
