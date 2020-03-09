package com.launchdarkly.sdk;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

@JsonAdapter(LDValueTypeAdapter.class)
final class LDValueBool extends LDValue {
  private static final LDValueBool TRUE = new LDValueBool(true);
  private static final LDValueBool FALSE = new LDValueBool(false);
  
  private final boolean value;
  
  static LDValueBool fromBoolean(boolean value) {
    return value ? TRUE : FALSE;
  }
  
  private LDValueBool(boolean value) {
    this.value = value;
  }
  
  public LDValueType getType() {
    return LDValueType.BOOLEAN;
  }

  @Override
  public boolean booleanValue() {
    return value;
  }

  @Override
  public String toJsonString() {
    return value ? "true" : "false";
  }
  
  @Override
  void write(JsonWriter writer) throws IOException {
    writer.value(value);
  }
}
