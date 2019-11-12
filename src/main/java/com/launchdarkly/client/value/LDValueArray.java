package com.launchdarkly.client.value;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

@JsonAdapter(LDValueTypeAdapter.class)
final class LDValueArray extends LDValue {
  private static final LDValueArray EMPTY = new LDValueArray(ImmutableList.<LDValue>of());
  private final ImmutableList<LDValue> list;

  static LDValueArray fromList(ImmutableList<LDValue> list) {
    return list.isEmpty() ? EMPTY : new LDValueArray(list);
  }

  private LDValueArray(ImmutableList<LDValue> list) {
    this.list = list;
  }
  
  public LDValueType getType() {
    return LDValueType.ARRAY;
  }
  
  @Override
  public int size() {
    return list.size();
  }
  
  @Override
  public Iterable<LDValue> values() {
    return list;
  }
  
  @Override
  public LDValue get(int index) {
    if (index >= 0 && index < list.size()) {
      return list.get(index);
    }
    return ofNull();
  }

  @Override
  void write(JsonWriter writer) throws IOException {
    writer.beginArray();
    for (LDValue v: list) {
      v.write(writer);
    }
    writer.endArray();
  }
  
  @Override
  @SuppressWarnings("deprecation")
  JsonElement computeJsonElement() {
    JsonArray a = new JsonArray();
    for (LDValue item: list) {
      a.add(item.asUnsafeJsonElement());
    }
    return a;
  }
}
