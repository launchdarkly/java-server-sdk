package com.launchdarkly.client.value;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonWriter;
import com.launchdarkly.client.LDClientInterface;
import com.launchdarkly.client.LDUser;

import java.io.IOException;

/**
 * An immutable instance of any data type that is allowed in JSON.
 * <p>
 * This is used with the client's {@link LDClientInterface#jsonValueVariation(String, LDUser, LDValue)}
 * method, and is also used internally to hold feature flag values.
 * <p>
 * While the LaunchDarkly SDK uses Gson for JSON parsing, some of the Gson value types (object
 * and array) are mutable. In contexts where it is important for data to remain immutable after
 * it is created, these values are represented with {@link LDValue} instead. It is easily
 * convertible to primitive types and provides array element/object property accessors.
 * 
 * @since 4.8.0
 */
@JsonAdapter(LDValueTypeAdapter.class)
public abstract class LDValue {
  static final Gson gson = new Gson();
  
  private boolean haveComputedJsonElement = false;
  private JsonElement computedJsonElement = null;
  
  /**
   * Returns the same value if non-null, or {@link #ofNull()} if null.
   * 
   * @param value an {@link LDValue} or null
   * @return an {@link LDValue} which will never be a null reference
   */
  public static LDValue normalize(LDValue value) {
    return value == null ? ofNull() : value;
  }
  
  /**
   * Returns an instance for a null value. The same instance is always used.
   * 
   * @return an LDValue containing null
   */
  public static LDValue ofNull() {
    return LDValueNull.INSTANCE;
  }
  
  /**
   * Returns an instance for a boolean value. The same instances for {@code true} and {@code false}
   * are always used.
   * 
   * @param value a boolean value
   * @return an LDValue containing that value
   */
  public static LDValue of(boolean value) {
    return LDValueBool.fromBoolean(value);
  }
  
  /**
   * Returns an instance for a numeric value.
   * 
   * @param value an integer numeric value
   * @return an LDValue containing that value
   */
  public static LDValue of(int value) {
    return LDValueNumber.fromDouble(value);
  }
  
  /**
   * Returns an instance for a numeric value.
   * 
   * @param value a floating-point numeric value
   * @return an LDValue containing that value
   */
  public static LDValue of(float value) {
    return LDValueNumber.fromDouble(value);
  }
  
  /**
   * Returns an instance for a numeric value.
   * 
   * @param value a floating-point numeric value
   * @return an LDValue containing that value
   */
  public static LDValue of(double value) {
    return LDValueNumber.fromDouble(value);
  }
  
  /**
   * Returns an instance for a string value (or a null).
   * 
   * @param value a nullable String reference
   * @return an LDValue containing a string, or {@link #ofNull()} if the value was null.
   */
  public static LDValue of(String value) {
    return value == null ? ofNull() : LDValueString.fromString(value);
  }

  /**
   * Starts building an array value.
   * <pre>
   *     LDValue arrayOfInts = LDValue.buildArray().add(LDValue.int(1), LDValue.int(2)).build():
   * </pre>
   * @return an {@link ArrayBuilder}
   */
  public static ArrayBuilder buildArray() {
    return new ArrayBuilder();
  }

  /**
   * Starts building an object value.
   * <pre>
   *     LDValue objectVal = LDValue.buildObject().put("key", LDValue.int(1)).build():
   * </pre>
   * @return an {@link ObjectBuilder}
   */
  public static ObjectBuilder buildObject() {
    return new ObjectBuilder();
  }
  
  /**
   * Returns an instance based on a {@link JsonElement} value. If the value is a complex type, it is
   * deep-copied; primitive types are used as is.
   * 
   * @param value a nullable {@link JsonElement} reference
   * @return an LDValue containing the specified value, or {@link #ofNull()} if the value was null.
   * @deprecated The Gson types may be removed from the public API at some point; it is preferable to
   * use factory methods like {@link #of(boolean)}.
   */
  @Deprecated
  public static LDValue fromJsonElement(JsonElement value) {
    return value == null || value.isJsonNull() ? ofNull() : LDValueJsonElement.copyValue(value); 
  }
  
  /**
   * Returns an instance that wraps an existing {@link JsonElement} value without copying it. This
   * method exists only to support deprecated SDK methods where a {@link JsonElement} is needed, to
   * avoid the inefficiency of a deep-copy; application code should not use it, since it can break
   * the immutability contract of {@link LDValue}.
   * 
   * @param value a nullable {@link JsonElement} reference
   * @return an LDValue containing the specified value, or {@link #ofNull()} if the value was null.
   * @deprecated This method will be removed in a future version. Application code should use
   * {@link #fromJsonElement(JsonElement)} or, preferably, factory methods like {@link #of(boolean)}.
   */
  @Deprecated
  public static LDValue unsafeFromJsonElement(JsonElement value) {
    return value == null || value.isJsonNull() ? ofNull() : LDValueJsonElement.wrapUnsafeValue(value);
  }
  
  /**
   * Gets the JSON type for this value.
   * 
   * @return the appropriate {@link LDValueType}
   */
  public abstract LDValueType getType();
  
  /**
   * Tests whether this value is a null.
   * 
   * @return {@code true} if this is a null value
   */
  public boolean isNull() {
    return false;
  }

  /**
   * Returns this value as a boolean if it is explicitly a boolean. Otherwise returns {@code false}.
   * 
   * @return a boolean
   */
  public boolean booleanValue() {
    return false;
  }
  
  /**
   * Tests whether this value is a number (not a numeric string).
   * 
   * @return {@code true} if this is a numeric value
   */
  public boolean isNumber() {
    return false;
  }
  
  /**
   * Tests whether this value is a number that is also an integer.
   * <p>
   * JSON does not have separate types for integer and floating-point values; they are both just
   * numbers. This method returns true if and only if the actual numeric value has no fractional
   * component, so {@code LDValue.of(2).isInt()} and {@code LDValue.of(2.0f).isInt()} are both true.
   * 
   * @return {@code true} if this is an integer value
   */
  public boolean isInt() {
    return false;
  }
  
  /**
   * Returns this value as an {@code int} if it is numeric. Returns zero for all non-numeric values.
   * <p>
   * If the value is a number but not an integer, it will be rounded toward zero (truncated).
   * This is consistent with Java casting behavior, and with most other LaunchDarkly SDKs.
   * 
   * @return an {@code int} value
   */
  public int intValue() {
    return 0;
  }
  
  /**
   * Returns this value as a {@code float} if it is numeric. Returns zero for all non-numeric values.
   * 
   * @return a {@code float} value
   */
  public float floatValue() {
    return 0;
  }

  /**
   * Returns this value as a {@code double} if it is numeric. Returns zero for all non-numeric values.
   * 
   * @return a {@code double} value
   */
  public double doubleValue() {
    return 0;
  }
  
  /**
   * Returns this value as a {@code String} if it is a string. Returns {@code null} for all non-string values.
   * 
   * @return a nullable string value
   */
  public String stringValue() {
    return null;
  }
  
  /**
   * Returns the number of elements in an array or object. Returns zero for all other types.
   * 
   * @return the number of array elements or object properties
   */
  public int size() {
    return 0;
  }

  /**
   * Enumerates the property names in an object. Returns an empty iterable for all other types.
   * 
   * @return the property names
   */
  public Iterable<String> keys() {
    return ImmutableList.of();
  }
  
  /**
   * Enumerates the values in an array or object. Returns an empty iterable for all other types.
   * 
   * @return an iterable of {@link LDValue} values
   */
  public Iterable<LDValue> values() {
    return ImmutableList.of();
  }
  
  /**
   * Returns an array element by index. Returns {@link #ofNull()} if this is not an array or if the
   * index is out of range (will never throw an exception).
   * 
   * @param index the array index
   * @return the element value or {@link #ofNull()}
   */
  public LDValue get(int index) {
    return ofNull();
  }
  
  /**
   * Returns an object property by name. Returns {@link #ofNull()} if this is not an object or if the
   * key is not found (will never throw an exception).
   * 
   * @param name the property name
   * @return the property value or {@link #ofNull()}
   */
  public LDValue get(String name) {
    return ofNull();
  }
  
  /**
   * Converts this value to its JSON serialization.
   * 
   * @return a JSON string
   */
  public String toJsonString() {
    return gson.toJson(this);
  }
  
  /**
   * Converts this value to a {@link JsonElement}. If the value is a complex type, it is deep-copied
   * deep-copied, so modifying the return value will not affect the {@link LDValue}.
   * 
   * @return a {@link JsonElement}, or {@code null} if the value is a null
   * @deprecated The Gson types may be removed from the public API at some point; it is preferable to
   * use getters like {@link #booleanValue()} and {@link #getType()}.
   */
  public JsonElement asJsonElement() {
    return LDValueJsonElement.deepCopy(asUnsafeJsonElement());
  }
  
  /**
   * Returns the original {@link JsonElement} if the value was created from one, otherwise converts the
   * value to a {@link JsonElement}. This method exists only to support deprecated SDK methods where a
   * {@link JsonElement} is needed, to avoid the inefficiency of a deep-copy; application code should not
   * use it, since it can break the immutability contract of {@link LDValue}.
   * 
   * @return a {@link JsonElement}, or {@code null} if the value is a null
   * @deprecated This method will be removed in a future version. Application code should always use
   * {@link #asJsonElement()}.
   */
  @Deprecated
  public JsonElement asUnsafeJsonElement() {
    // Lazily compute this value
    synchronized (this) {
      if (!haveComputedJsonElement) {
        computedJsonElement = computeJsonElement();
        haveComputedJsonElement = true;
      }
      return computedJsonElement;
    }
  }
  
  abstract JsonElement computeJsonElement();
  
  abstract void write(JsonWriter writer) throws IOException;
  
  static boolean isInteger(double value) {
    return value == (double)((int)value);
  }
  
  @Override
  public String toString() {
    return toJsonString();
  }
  
  // equals() and hashCode() are defined here in the base class so that we don't have to worry about
  // whether a value is stored as LDValueJsonElement vs. one of our own primitive types.
  
  @Override
  public boolean equals(Object o) {
    if (o instanceof LDValue) {
      LDValue other = (LDValue)o;
      if (getType() == other.getType()) {
        switch (getType()) {
        case NULL: return other.isNull();
        case BOOLEAN: return booleanValue() == other.booleanValue();
        case NUMBER: return doubleValue() == other.doubleValue();
        case STRING: return stringValue().equals(other.stringValue());
        case ARRAY:
          if (size() != other.size()) {
            return false;
          }
          for (int i = 0; i < size(); i++) {
            if (!get(i).equals(other.get(i))) {
              return false;
            }
          }
          return true; 
        case OBJECT:
          if (size() != other.size()) {
            return false;
          }
          for (String name: keys()) {
            if (!get(name).equals(other.get(name))) {
              return false;
            }
          }
          return true;
        }
      }
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    switch (getType()) {
    case NULL: return 0;
    case BOOLEAN: return booleanValue() ? 1 : 0;
    case NUMBER: return intValue();
    case STRING: return stringValue().hashCode();
    case ARRAY:
      int ah = 0;
      for (LDValue v: values()) {
        ah = ah * 31 + v.hashCode();
      }
      return ah;
    case OBJECT:
      int oh = 0;
      for (String name: keys()) {
        oh = (oh * 31 + name.hashCode()) * 31 + get(name).hashCode();
      }
      return oh;
    default: return 0;
    }
  }
}
