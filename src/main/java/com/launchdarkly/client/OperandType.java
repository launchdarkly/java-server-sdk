package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;
import com.launchdarkly.client.value.LDValue;

/**
 * Operator value that can be applied to {@link JsonPrimitive} objects. Incompatible types or other errors
 * will always yield false. This enum can be directly deserialized from JSON, avoiding the need for a mapping
 * of strings to operators.
 */
enum OperandType {
  string,
  number,
  date,
  semVer;
  
  public static OperandType bestGuess(LDValue value) {
    return value.isNumber() ? number : string;
  }
  
  public Object getValueAsType(LDValue value) {
    switch (this) {
    case string:
      return value.stringValue();
    case number:
      return value.isNumber() ? Double.valueOf(value.doubleValue()) : null;
    case date:
      return Util.jsonPrimitiveToDateTime(value);
    case semVer:
      if (!value.isString()) {
        return null;
      }
      try {
        return SemanticVersion.parse(value.stringValue(), true);
      } catch (SemanticVersion.InvalidVersionException e) {
        return null;
      }
    default:
      return null;
    }
  }
}
