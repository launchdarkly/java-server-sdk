package com.launchdarkly.sdk;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

@JsonAdapter(LDValueTypeAdapter.class)
final class LDValueString extends LDValue {
  private static final LDValueString EMPTY = new LDValueString("");
  private final String value;
  
  static LDValueString fromString(String value) {
    return value.isEmpty() ? EMPTY : new LDValueString(value);
  }
  
  private LDValueString(String value) {
    this.value = value;
  }
  
  public LDValueType getType() {
    return LDValueType.STRING;
  }
  
  @Override
  public boolean isString() {
    return true;
  }
  
  @Override
  public String stringValue() {
    return value;
  }

  @Override
  void write(JsonWriter writer) throws IOException {
    writer.value(value);
  }
}