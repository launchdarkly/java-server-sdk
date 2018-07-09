package com.launchdarkly.client;


import com.google.gson.JsonElement;

abstract class VariationType<T> {
  abstract T coerceValue(JsonElement result) throws EvaluationException;
  
  private VariationType() {
  }
  
  static VariationType<Boolean> Boolean = new VariationType<Boolean>() {
    Boolean coerceValue(JsonElement result) throws EvaluationException {
      if (result.isJsonPrimitive() && result.getAsJsonPrimitive().isBoolean()) {
        return result.getAsBoolean();
      }
      throw new EvaluationException("Feature flag evaluation expected result as boolean type, but got non-boolean type.");
    }
  };
  
  static VariationType<Integer> Integer = new VariationType<Integer>() {
    Integer coerceValue(JsonElement result) throws EvaluationException {
      if (result.isJsonPrimitive() && result.getAsJsonPrimitive().isNumber()) {
        return result.getAsInt();
      }
      throw new EvaluationException("Feature flag evaluation expected result as number type, but got non-number type.");
    }
  };
  
  static VariationType<Double> Double = new VariationType<Double>() {
    Double coerceValue(JsonElement result) throws EvaluationException {
      if (result.isJsonPrimitive() && result.getAsJsonPrimitive().isNumber()) {
        return result.getAsDouble();
      }
      throw new EvaluationException("Feature flag evaluation expected result as number type, but got non-number type.");
    }
  };
  
  static VariationType<String> String = new VariationType<String>() {
    String coerceValue(JsonElement result) throws EvaluationException {
      if (result.isJsonPrimitive() && result.getAsJsonPrimitive().isString()) {
        return result.getAsString();
      }
      throw new EvaluationException("Feature flag evaluation expected result as string type, but got non-string type.");
    }
  };
  
  static VariationType<JsonElement> Json = new VariationType<JsonElement>() {
    JsonElement coerceValue(JsonElement result) throws EvaluationException {
      return result;
    }
  };
}
