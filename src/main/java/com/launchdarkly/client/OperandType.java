package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;

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
  
  public static OperandType bestGuess(JsonPrimitive value) {
    return value.isNumber() ? number : string;
  }
  
  public Object getValueAsType(JsonPrimitive value) {
    switch (this) {
    case string:
      return value.getAsString();
    case number:
      if (value.isNumber()) {
        try {
          return value.getAsDouble();
        } catch (NumberFormatException e) {
        }
      }
      return null;
    case date:
      return Util.jsonPrimitiveToDateTime(value);
    case semVer:
      try {
        return SemanticVersion.parse(value.getAsString(), true);
      } catch (SemanticVersion.InvalidVersionException e) {
        return null;
      }
    default:
      return null;
    }
  }
}
