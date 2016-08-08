package com.launchdarkly.client;


import com.google.gson.JsonElement;

public enum VariationType {
  Boolean {
    @Override
    void assertResultType(JsonElement result) throws EvaluationException {
      if (result.isJsonPrimitive() && result.getAsJsonPrimitive().isBoolean()) {
        return;
      }
      throw new EvaluationException("Feature flag evaluation expected result as boolean type, but got non-boolean type.");
    }
  },
  Integer {
    @Override
    void assertResultType(JsonElement result) throws EvaluationException {
      if (result.isJsonPrimitive() && result.getAsJsonPrimitive().isNumber()) {
        return;
      }
      throw new EvaluationException("Feature flag evaluation expected result as number type, but got non-number type.");
    }
  },
  Double {
    @Override
    void assertResultType(JsonElement result) throws EvaluationException {
      if (result.isJsonPrimitive() && result.getAsJsonPrimitive().isNumber()) {
        return;
      }
      throw new EvaluationException("Feature flag evaluation expected result as number type, but got non-number type.");
    }
  },
  String {
    @Override
    void assertResultType(JsonElement result) throws EvaluationException {
      if (result.isJsonPrimitive() && result.getAsJsonPrimitive().isString()) {
        return;
      }
      throw new EvaluationException("Feature flag evaluation expected result as string type, but got non-string type.");
    }
  },
  Json {
    @Override
    void assertResultType(JsonElement result) {
    }
  };

  abstract void assertResultType(JsonElement result) throws EvaluationException;
}
