package com.launchdarkly.sdk;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

@JsonAdapter(LDValueTypeAdapter.class)
final class LDValueNull extends LDValue {
  static final LDValueNull INSTANCE = new LDValueNull();
  
  public LDValueType getType() {
    return LDValueType.NULL;
  }
  
  public boolean isNull() {
    return true;
  }

  @Override
  public String toJsonString() {
    return "null";
  }
  
  @Override
  void write(JsonWriter writer) throws IOException {
    writer.nullValue();
  }
}
