package com.launchdarkly.client.value;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings({"deprecation", "javadoc"})
public class LDValueTest {
  private static final Gson gson = new Gson();
  
  private static final int someInt = 3;
  private static final long someLong = 3;
  private static final float someFloat = 3.25f;
  private static final double someDouble = 3.25d;
  private static final String someString = "hi";
  
  private static final LDValue aTrueBoolValue = LDValue.of(true);
  private static final LDValue anIntValue = LDValue.of(someInt);
  private static final LDValue aLongValue = LDValue.of(someLong);
  private static final LDValue aFloatValue = LDValue.of(someFloat);
  private static final LDValue aDoubleValue = LDValue.of(someDouble);
  private static final LDValue aStringValue = LDValue.of(someString);
  private static final LDValue aNumericLookingStringValue = LDValue.of("3");
  private static final LDValue anArrayValue = LDValue.buildArray().add(LDValue.of(3)).build();
  private static final LDValue anObjectValue = LDValue.buildObject().put("1", LDValue.of("x")).build();
  
  private static final LDValue aTrueBoolValueFromJsonElement = LDValue.fromJsonElement(new JsonPrimitive(true));
  private static final LDValue anIntValueFromJsonElement = LDValue.fromJsonElement(new JsonPrimitive(someInt));
  private static final LDValue aLongValueFromJsonElement = LDValue.fromJsonElement(new JsonPrimitive(someLong));
  private static final LDValue aFloatValueFromJsonElement = LDValue.fromJsonElement(new JsonPrimitive(someFloat));
  private static final LDValue aDoubleValueFromJsonElement = LDValue.fromJsonElement(new JsonPrimitive(someDouble));
  private static final LDValue aStringValueFromJsonElement = LDValue.fromJsonElement(new JsonPrimitive(someString));
  private static final LDValue anArrayValueFromJsonElement = LDValue.fromJsonElement(anArrayValue.asJsonElement());
  private static final LDValue anObjectValueFromJsonElement = LDValue.fromJsonElement(anObjectValue.asJsonElement());
  
  @Test
  public void defaultValueJsonElementsAreReused() {
    assertSame(LDValue.ofNull(), LDValue.ofNull());
    assertSame(LDValue.of(true).asJsonElement(), LDValue.of(true).asJsonElement());
    assertSame(LDValue.of(false).asJsonElement(), LDValue.of(false).asJsonElement());
    assertSame(LDValue.of((int)0).asJsonElement(), LDValue.of((int)0).asJsonElement());
    assertSame(LDValue.of((long)0).asJsonElement(), LDValue.of((long)0).asJsonElement());
    assertSame(LDValue.of((float)0).asJsonElement(), LDValue.of((float)0).asJsonElement());
    assertSame(LDValue.of((double)0).asJsonElement(), LDValue.of((double)0).asJsonElement());
    assertSame(LDValue.of("").asJsonElement(), LDValue.of("").asJsonElement());
  }
  
  @Test
  public void canGetValueAsBoolean() {
    assertEquals(LDValueType.BOOLEAN, aTrueBoolValue.getType());
    assertTrue(aTrueBoolValue.booleanValue());
    assertEquals(LDValueType.BOOLEAN, aTrueBoolValueFromJsonElement.getType());
    assertTrue(aTrueBoolValueFromJsonElement.booleanValue());
  }
  
  @Test
  public void nonBooleanValueAsBooleanIsFalse() {
    LDValue[] values = new LDValue[] {
        LDValue.ofNull(),
        aStringValue,
        aStringValueFromJsonElement,
        anIntValue,
        anIntValueFromJsonElement,
        aLongValue,
        aLongValueFromJsonElement,
        aFloatValue,
        aFloatValueFromJsonElement,
        aDoubleValue,
        aDoubleValueFromJsonElement,
        anArrayValue,
        anArrayValueFromJsonElement,
        anObjectValue,
        anObjectValueFromJsonElement
    };
    for (LDValue value: values) {
      assertNotEquals(value.toString(), LDValueType.BOOLEAN, value.getType());
      assertFalse(value.toString(), value.booleanValue());
    }
  }
  
  @Test
  public void canGetValueAsString() {
    assertEquals(LDValueType.STRING, aStringValue.getType());
    assertEquals(someString, aStringValue.stringValue());
    assertEquals(LDValueType.STRING, aStringValueFromJsonElement.getType());
    assertEquals(someString, aStringValueFromJsonElement.stringValue());
  }

  @Test
  public void nonStringValueAsStringIsNull() {
    LDValue[] values = new LDValue[] {
        LDValue.ofNull(),
        aTrueBoolValue,
        aTrueBoolValueFromJsonElement,
        anIntValue,
        anIntValueFromJsonElement,
        aLongValue,
        aLongValueFromJsonElement,
        aFloatValue,
        aFloatValueFromJsonElement,
        aDoubleValue,
        aDoubleValueFromJsonElement,
        anArrayValue,
        anArrayValueFromJsonElement,
        anObjectValue,
        anObjectValueFromJsonElement
    };
    for (LDValue value: values) {
      assertNotEquals(value.toString(), LDValueType.STRING, value.getType());
      assertNull(value.toString(), value.stringValue());
    }
  }
  
  @Test
  public void nullStringConstructorGivesNullInstance() {
    assertEquals(LDValue.ofNull(), LDValue.of((String)null));
  }
  
  @Test
  public void canGetIntegerValueOfAnyNumericType() {
    LDValue[] values = new LDValue[] {
        LDValue.of(3),
        LDValue.of(3L),
        LDValue.of(3.0f),
        LDValue.of(3.25f),
        LDValue.of(3.75f),
        LDValue.of(3.0d),
        LDValue.of(3.25d),
        LDValue.of(3.75d)
    };
    for (LDValue value: values) {
      assertEquals(value.toString(), LDValueType.NUMBER, value.getType());
      assertEquals(value.toString(), 3, value.intValue());
      assertEquals(value.toString(), 3L, value.longValue());
    }
  }
  
  @Test
  public void canGetFloatValueOfAnyNumericType() {
    LDValue[] values = new LDValue[] {
        LDValue.of(3),
        LDValue.of(3L),
        LDValue.of(3.0f),
        LDValue.of(3.0d),
    };
    for (LDValue value: values) {
      assertEquals(value.toString(), LDValueType.NUMBER, value.getType());
      assertEquals(value.toString(), 3.0f, value.floatValue(), 0);
    }
  }
  
  @Test
  public void canGetDoubleValueOfAnyNumericType() {
    LDValue[] values = new LDValue[] {
        LDValue.of(3),
        LDValue.of(3L),
        LDValue.of(3.0f),
        LDValue.of(3.0d),
    };
    for (LDValue value: values) {
      assertEquals(value.toString(), LDValueType.NUMBER, value.getType());
      assertEquals(value.toString(), 3.0d, value.doubleValue(), 0);
    }
  }

  @Test
  public void nonNumericValueAsNumberIsZero() {
    LDValue[] values = new LDValue[] {
        LDValue.ofNull(),
        aTrueBoolValue,
        aTrueBoolValueFromJsonElement,
        aStringValue,
        aStringValueFromJsonElement,
        aNumericLookingStringValue,
        anArrayValue,
        anArrayValueFromJsonElement,
        anObjectValue,
        anObjectValueFromJsonElement
    };
    for (LDValue value: values) {
      assertNotEquals(value.toString(), LDValueType.NUMBER, value.getType());
      assertEquals(value.toString(), 0, value.intValue());
      assertEquals(value.toString(), 0f, value.floatValue(), 0);
      assertEquals(value.toString(), 0d, value.doubleValue(), 0);
    }
  }
  
  @Test
  public void canGetSizeOfArrayOrObject() {
    assertEquals(1, anArrayValue.size());
    assertEquals(1, anArrayValueFromJsonElement.size());
  }
  
  @Test
  public void arrayCanGetItemByIndex() {
    assertEquals(LDValueType.ARRAY, anArrayValue.getType());
    assertEquals(LDValueType.ARRAY, anArrayValueFromJsonElement.getType());
    assertEquals(LDValue.of(3), anArrayValue.get(0));
    assertEquals(LDValue.of(3), anArrayValueFromJsonElement.get(0));
    assertEquals(LDValue.ofNull(), anArrayValue.get(-1));
    assertEquals(LDValue.ofNull(), anArrayValue.get(1));
    assertEquals(LDValue.ofNull(), anArrayValueFromJsonElement.get(-1));
    assertEquals(LDValue.ofNull(), anArrayValueFromJsonElement.get(1));
  }
  
  @Test
  public void arrayCanBeEnumerated() {
    LDValue a = LDValue.of("a");
    LDValue b = LDValue.of("b");
    List<LDValue> values = new ArrayList<>();
    for (LDValue v: LDValue.buildArray().add(a).add(b).build().values()) {
      values.add(v);
    }
    assertEquals(ImmutableList.of(a, b), values);
  }
  
  @Test
  public void nonArrayValuesBehaveLikeEmptyArray() {
    LDValue[] values = new LDValue[] {
        LDValue.ofNull(),
        aTrueBoolValue,
        aTrueBoolValueFromJsonElement,
        anIntValue,
        aLongValue,
        aFloatValue,
        aDoubleValue,
        aStringValue,
        aNumericLookingStringValue,
    };
    for (LDValue value: values) {
      assertEquals(value.toString(), 0, value.size());
      assertEquals(value.toString(), LDValue.of(null), value.get(-1));
      assertEquals(value.toString(), LDValue.of(null), value.get(0));
      for (@SuppressWarnings("unused") LDValue v: value.values()) {
        fail(value.toString());
      }
    }
  }
  
  @Test
  public void canGetSizeOfObject() {
    assertEquals(1, anObjectValue.size());
    assertEquals(1, anObjectValueFromJsonElement.size());
  }
  
  @Test
  public void objectCanGetValueByName() {
    assertEquals(LDValueType.OBJECT, anObjectValue.getType());
    assertEquals(LDValueType.OBJECT, anObjectValueFromJsonElement.getType());
    assertEquals(LDValue.of("x"), anObjectValue.get("1"));
    assertEquals(LDValue.of("x"), anObjectValueFromJsonElement.get("1"));
    assertEquals(LDValue.ofNull(), anObjectValue.get(null));
    assertEquals(LDValue.ofNull(), anObjectValueFromJsonElement.get(null));
    assertEquals(LDValue.ofNull(), anObjectValue.get("2"));
    assertEquals(LDValue.ofNull(), anObjectValueFromJsonElement.get("2"));
  }
  
  @Test
  public void objectKeysCanBeEnumerated() {
    List<String> keys = new ArrayList<>();
    for (String key: LDValue.buildObject().put("1", LDValue.of("x")).put("2", LDValue.of("y")).build().keys()) {
      keys.add(key);
    }
    keys.sort(null);
    assertEquals(ImmutableList.of("1", "2"), keys);
  }

  @Test
  public void objectValuesCanBeEnumerated() {
    List<String> values = new ArrayList<>();
    for (LDValue value: LDValue.buildObject().put("1", LDValue.of("x")).put("2", LDValue.of("y")).build().values()) {
      values.add(value.stringValue());
    }
    values.sort(null);
    assertEquals(ImmutableList.of("x", "y"), values);
  }
  
  @Test
  public void nonObjectValuesBehaveLikeEmptyObject() {
    LDValue[] values = new LDValue[] {
        LDValue.ofNull(),
        aTrueBoolValue,
        aTrueBoolValueFromJsonElement,
        anIntValue,
        aLongValue,
        aFloatValue,
        aDoubleValue,
        aStringValue,
        aNumericLookingStringValue,
    };
    for (LDValue value: values) {
      assertEquals(value.toString(), LDValue.of(null), value.get(null));
      assertEquals(value.toString(), LDValue.of(null), value.get("1"));
      for (@SuppressWarnings("unused") String key: value.keys()) {
        fail(value.toString());
      }
    }
  }
  
  @Test
  public void samePrimitivesWithOrWithoutJsonElementAreEqual() {
    assertEquals(aTrueBoolValue, aTrueBoolValueFromJsonElement);
    assertEquals(anIntValue, anIntValueFromJsonElement);
    assertEquals(aLongValue, aLongValueFromJsonElement);
    assertEquals(aFloatValue, aFloatValueFromJsonElement);
    assertEquals(aStringValue, aStringValueFromJsonElement);
    assertEquals(anArrayValue, anArrayValueFromJsonElement);
    assertEquals(anObjectValue, anObjectValueFromJsonElement);
  }
  
  @Test
  public void canUseLongTypeForNumberGreaterThanMaxInt() {
    long n = (long)Integer.MAX_VALUE + 1;
    assertEquals(n, LDValue.of(n).longValue());
    assertEquals(n, LDValue.Convert.Long.toType(LDValue.of(n)).longValue());
    assertEquals(n, LDValue.Convert.Long.fromType(n).longValue());
  }

  @Test
  public void canUseDoubleTypeForNumberGreaterThanMaxFloat() {
    double n = (double)Float.MAX_VALUE + 1;
    assertEquals(n, LDValue.of(n).doubleValue(), 0);
    assertEquals(n, LDValue.Convert.Double.toType(LDValue.of(n)).doubleValue(), 0);
    assertEquals(n, LDValue.Convert.Double.fromType(n).doubleValue(), 0);
  }
  
  @Test
  public void testToJsonString() {
    assertEquals("null", LDValue.ofNull().toJsonString());
    assertEquals("true", aTrueBoolValue.toJsonString());
    assertEquals("true", aTrueBoolValueFromJsonElement.toJsonString());
    assertEquals("false", LDValue.of(false).toJsonString());
    assertEquals(String.valueOf(someInt), anIntValue.toJsonString());
    assertEquals(String.valueOf(someInt), anIntValueFromJsonElement.toJsonString());
    assertEquals(String.valueOf(someLong), aLongValue.toJsonString());
    assertEquals(String.valueOf(someLong), aLongValueFromJsonElement.toJsonString());
    assertEquals(String.valueOf(someFloat), aFloatValue.toJsonString());
    assertEquals(String.valueOf(someFloat), aFloatValueFromJsonElement.toJsonString());
    assertEquals(String.valueOf(someDouble), aDoubleValue.toJsonString());
    assertEquals(String.valueOf(someDouble), aDoubleValueFromJsonElement.toJsonString());
    assertEquals("\"hi\"", aStringValue.toJsonString());
    assertEquals("\"hi\"", aStringValueFromJsonElement.toJsonString());
    assertEquals("[3]", anArrayValue.toJsonString());
    assertEquals("[3.0]", anArrayValueFromJsonElement.toJsonString());
    assertEquals("{\"1\":\"x\"}", anObjectValue.toJsonString());
    assertEquals("{\"1\":\"x\"}", anObjectValueFromJsonElement.toJsonString());
  }
  
  @Test
  public void testDefaultGsonSerialization() {
    LDValue[] values = new LDValue[] {
        LDValue.ofNull(),
        aTrueBoolValue,
        aTrueBoolValueFromJsonElement,
        anIntValue,
        anIntValueFromJsonElement,
        aLongValue,
        aLongValueFromJsonElement,
        aFloatValue,
        aFloatValueFromJsonElement,
        aDoubleValue,
        aDoubleValueFromJsonElement,
        aStringValue,
        aStringValueFromJsonElement,
        anArrayValue,
        anArrayValueFromJsonElement,
        anObjectValue,
        anObjectValueFromJsonElement
    };
    for (LDValue value: values) {
      assertEquals(value.toString(), value.toJsonString(), gson.toJson(value));
      assertEquals(value.toString(), value, LDValue.normalize(gson.fromJson(value.toJsonString(), LDValue.class)));
    }
  }
  
  @Test
  public void valueToJsonElement() {
    assertNull(LDValue.ofNull().asJsonElement());
    assertEquals(new JsonPrimitive(true), aTrueBoolValue.asJsonElement());
    assertEquals(new JsonPrimitive(someInt), anIntValue.asJsonElement());
    assertEquals(new JsonPrimitive(someLong), aLongValue.asJsonElement());
    assertEquals(new JsonPrimitive(someFloat), aFloatValue.asJsonElement());
    assertEquals(new JsonPrimitive(someDouble), aDoubleValue.asJsonElement());
    assertEquals(new JsonPrimitive(someString), aStringValue.asJsonElement());
  }
  
  @Test
  public void testTypeConversions() {
    testTypeConversion(LDValue.Convert.Boolean, new Boolean[] { true, false }, LDValue.of(true), LDValue.of(false));
    testTypeConversion(LDValue.Convert.Integer, new Integer[] { 1, 2 }, LDValue.of(1), LDValue.of(2));
    testTypeConversion(LDValue.Convert.Long, new Long[] { 1L, 2L }, LDValue.of(1L), LDValue.of(2L));
    testTypeConversion(LDValue.Convert.Float, new Float[] { 1.5f, 2.5f }, LDValue.of(1.5f), LDValue.of(2.5f));
    testTypeConversion(LDValue.Convert.Double, new Double[] { 1.5d, 2.5d }, LDValue.of(1.5d), LDValue.of(2.5d));
    testTypeConversion(LDValue.Convert.String, new String[] { "a", "b" }, LDValue.of("a"), LDValue.of("b"));
  }
  
  private <T> void testTypeConversion(LDValue.Converter<T> converter, T[] values, LDValue... ldValues) {
    ArrayBuilder ab = LDValue.buildArray();
    for (LDValue v: ldValues) {
      ab.add(v);
    }
    LDValue arrayValue = ab.build();
    assertEquals(arrayValue, converter.arrayOf(values));
    ImmutableList.Builder<T> lb = ImmutableList.<T>builder();
    for (T v: values) {
      lb.add(v);
    }
    ImmutableList<T> list = lb.build();
    assertEquals(arrayValue, converter.arrayFrom(list));
    assertEquals(list, ImmutableList.copyOf(arrayValue.valuesAs(converter)));
    
    ObjectBuilder ob = LDValue.buildObject();
    int i = 0;
    for (LDValue v: ldValues) {
      ob.put(String.valueOf(++i), v);
    }
    LDValue objectValue = ob.build();
    ImmutableMap.Builder<String, T> mb = ImmutableMap.<String, T>builder();
    i = 0;
    for (T v: values) {
      mb.put(String.valueOf(++i), v);
    }
    ImmutableMap<String, T> map = mb.build();
    assertEquals(objectValue, converter.objectFrom(map));
  }
}
