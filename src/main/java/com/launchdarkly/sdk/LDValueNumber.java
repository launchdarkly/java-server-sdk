package com.launchdarkly.sdk;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

@JsonAdapter(LDValueTypeAdapter.class)
final class LDValueNumber extends LDValue {
  private static final LDValueNumber ZERO = new LDValueNumber(0);
  private final double value;
  
  static LDValueNumber fromDouble(double value) {
    return value == 0 ? ZERO : new LDValueNumber(value);
  }
  
  private LDValueNumber(double value) {
    this.value = value;
  }
  
  public LDValueType getType() {
    return LDValueType.NUMBER;
  }
  
  @Override
  public boolean isNumber() {
    return true;
  }
  
  @Override
  public boolean isInt() {
    return isInteger(value);
  }
  
  @Override
  public int intValue() {
    return (int)value;
  }

  @Override
  public long longValue() {
    return (long)value;
  }
  
  @Override
  public float floatValue() {
    return (float)value;
  }

  @Override
  public double doubleValue() {
    return value;
  }

  @Override
  public String toJsonString() {
    return isInt() ? String.valueOf(intValue()) : String.valueOf(value);
  }
  
  @Override
  void write(JsonWriter writer) throws IOException {
    if (isInt()) {
      writer.value(intValue());
    } else {
      writer.value(value);
    }
  }
}
