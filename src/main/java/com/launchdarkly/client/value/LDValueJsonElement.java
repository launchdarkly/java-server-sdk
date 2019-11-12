package com.launchdarkly.client.value;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

@JsonAdapter(LDValueTypeAdapter.class)
final class LDValueJsonElement extends LDValue {
  private final JsonElement value;
  private final LDValueType type;
  
  static LDValueJsonElement copyValue(JsonElement value) {
    return new LDValueJsonElement(deepCopy(value));
  }
  
  static LDValueJsonElement wrapUnsafeValue(JsonElement value) {
    return new LDValueJsonElement(value);
  }
  
  LDValueJsonElement(JsonElement value) {
    this.value = value;
    type = typeFromValue(value);
  }
  
  private static LDValueType typeFromValue(JsonElement value) {
    if (value != null) {
      if (value.isJsonPrimitive()) {
        JsonPrimitive p = value.getAsJsonPrimitive();        
        if (p.isBoolean()) {
          return LDValueType.BOOLEAN;
        } else if (p.isNumber()) {
          return LDValueType.NUMBER;
        } else if (p.isString()) {
          return LDValueType.STRING;
        } else {
          return LDValueType.NULL;
        }
      } else if (value.isJsonArray()) {
        return LDValueType.ARRAY;
      } else if (value.isJsonObject()) {
        return LDValueType.OBJECT;
      }
    }
    return LDValueType.NULL;
  }
  
  public LDValueType getType() {
    return type;
  }
  
  @Override
  public boolean isNull() {
    return value == null;
  }
  
  @Override
  public boolean booleanValue() {
    return type == LDValueType.BOOLEAN && value.getAsBoolean();
  }
  
  @Override
  public boolean isNumber() {
    return type == LDValueType.NUMBER;
  }
  
  @Override
  public boolean isInt() {
    return type == LDValueType.NUMBER && isInteger(value.getAsFloat());
  }
  
  @Override
  public int intValue() {
    return type == LDValueType.NUMBER ? (int)value.getAsFloat() : 0; // don't rely on their rounding behavior
  }

  @Override
  public long longValue() {
    return type == LDValueType.NUMBER ? (long)value.getAsDouble() : 0; // don't rely on their rounding behavior
  }
  
  @Override
  public float floatValue() {
    return type == LDValueType.NUMBER ? value.getAsFloat() : 0;
  }

  @Override
  public double doubleValue() {
    return type == LDValueType.NUMBER ? value.getAsDouble() : 0;
  }
  
  @Override
  public boolean isString() {
    return type == LDValueType.STRING;
  }
  
  @Override
  public String stringValue() {
    return type == LDValueType.STRING ? value.getAsString() : null;
  }

  @Override
  public int size() {
    switch (type) {
    case ARRAY:
      return value.getAsJsonArray().size();
    case OBJECT:
      return value.getAsJsonObject().size();
    default:
      return 0;
    }
  }

  @Override
  public Iterable<String> keys() {
    if (type == LDValueType.OBJECT) {
      return Iterables.transform(value.getAsJsonObject().entrySet(), new Function<Map.Entry<String, JsonElement>, String>() {
        public String apply(Map.Entry<String, JsonElement> e) {
          return e.getKey();
        }
      });
    }
    return ImmutableList.of();
  }
  
  @SuppressWarnings("deprecation")
  @Override
  public Iterable<LDValue> values() {
    switch (type) {
    case ARRAY:
      return Iterables.transform(value.getAsJsonArray(), new Function<JsonElement, LDValue>() {
        public LDValue apply(JsonElement e) {
          return unsafeFromJsonElement(e);
        }
      });
    case OBJECT:
      return Iterables.transform(value.getAsJsonObject().entrySet(), new Function<Map.Entry<String, JsonElement>, LDValue>() {
        public LDValue apply(Map.Entry<String, JsonElement> e) {
          return unsafeFromJsonElement(e.getValue());
        }
      });
    default: return ImmutableList.of();
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public LDValue get(int index) {
    if (type == LDValueType.ARRAY) {
      JsonArray a = value.getAsJsonArray();
      if (index >= 0 && index < a.size()) {
        return unsafeFromJsonElement(a.get(index));
      }
    }
    return ofNull();
  }

  @SuppressWarnings("deprecation")
  @Override
  public LDValue get(String name) {
    if (type == LDValueType.OBJECT) {
      return unsafeFromJsonElement(value.getAsJsonObject().get(name));
    }
    return ofNull();
  }

  @Override
  void write(JsonWriter writer) throws IOException {
    gson.toJson(value, writer);
  }
  
  @Override
  JsonElement computeJsonElement() {
    return value;
  }
  
  static JsonElement deepCopy(JsonElement value) { // deepCopy was added to Gson in 2.8.2
    if (value != null && !value.isJsonPrimitive()) {
      if (value.isJsonArray()) {
        JsonArray a = value.getAsJsonArray();
        JsonArray ret = new JsonArray();
        for (JsonElement e: a) {
          ret.add(deepCopy(e));
        }
        return ret;
      } else if (value.isJsonObject()) {
        JsonObject o = value.getAsJsonObject();
        JsonObject ret = new JsonObject();
        for (Entry<String, JsonElement> e: o.entrySet()) {
          ret.add(e.getKey(), deepCopy(e.getValue()));
        }
        return ret;
      }
    }
    return value;
  }
}
