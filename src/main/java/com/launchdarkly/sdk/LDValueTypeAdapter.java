package com.launchdarkly.sdk;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

final class LDValueTypeAdapter extends TypeAdapter<LDValue>{
  static final LDValueTypeAdapter INSTANCE = new LDValueTypeAdapter();
  
  @Override
  public LDValue read(JsonReader reader) throws IOException {
    JsonToken token = reader.peek();
    switch (token) {
    case BEGIN_ARRAY:
      ArrayBuilder ab = LDValue.buildArray();
      reader.beginArray();
      while (reader.peek() != JsonToken.END_ARRAY) {
        ab.add(read(reader));
      }
      reader.endArray();
      return ab.build();
    case BEGIN_OBJECT:
      ObjectBuilder ob = LDValue.buildObject();
      reader.beginObject();
      while (reader.peek() != JsonToken.END_OBJECT) {
        String key = reader.nextName();
        LDValue value = read(reader);
        ob.put(key, value);
      }
      reader.endObject();
      return ob.build();
    case BOOLEAN:
      return LDValue.of(reader.nextBoolean());
    case NULL:
      reader.nextNull();
      return LDValue.ofNull();
    case NUMBER:
      return LDValue.of(reader.nextDouble());
    case STRING:
      return LDValue.of(reader.nextString());
    default:
      return null;
    }
  }

  @Override
  public void write(JsonWriter writer, LDValue value) throws IOException {
    value.write(writer);
  }
}
