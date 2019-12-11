package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

abstract class JsonHelpers {
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
