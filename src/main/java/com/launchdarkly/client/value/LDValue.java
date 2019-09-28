package com.launchdarkly.client.value;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonWriter;
import com.launchdarkly.client.LDClientInterface;
import com.launchdarkly.client.LDUser;

import java.io.IOException;
import java.util.Map;

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
   * <pre><code>
   *     LDValue arrayOfInts = LDValue.buildArray().add(LDValue.int(1), LDValue.int(2)).build():
   * </code></pre>
   * If the values are all of the same type, you may also use {@link LDValue.Converter#arrayFrom(Iterable)}
   * or {@link LDValue.Converter#arrayOf(Object...)}.
   * 
   * @return an {@link ArrayBuilder}
   */
  public static ArrayBuilder buildArray() {
    return new ArrayBuilder();
  }

  /**
   * Starts building an object value.
   * <pre><code>
   *     LDValue objectVal = LDValue.buildObject().put("key", LDValue.int(1)).build():
   * </code></pre>
   * If the values are all of the same type, you may also use {@link LDValue.Converter#objectFrom(Map)}.
   * 
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
   * Tests whether this value is a string.
   * 
   * @return {@code true} if this is a string value
   */
  public boolean isString() {
    return false;
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
   * Enumerates the values in an array or object, converting them to a specific type. Returns an empty
   * iterable for all other types.
   * <p>
   * This is an efficient method because it does not copy values to a new list, but returns a view
   * into the existing array.
   * <p>
   * Example:
   * <pre><code>
   *     LDValue anArrayOfInts = LDValue.Convert.Integer.arrayOf(1, 2, 3);
   *     for (int i: anArrayOfInts.valuesAs(LDValue.Convert.Integer)) { println(i); }
   * </code></pre>
   * 
   * @param converter the {@link Converter} for the specified type
   * @return an iterable of values of the specified type
   */
  public <T> Iterable<T> valuesAs(final Converter<T> converter) {
    return Iterables.transform(values(), new Function<LDValue, T>() {
      public T apply(LDValue value) {
        return converter.toType(value);
      }
    });
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
  
  /**
   * Defines a conversion between {@link LDValue} and some other type.
   * <p>
   * Besides converting individual values, this provides factory methods like {@link #arrayOf}
   * which transform a collection of the specified type to the corresponding {@link LDValue}
   * complex type.
   * 
   * @param <T> the type to convert from/to
   * @since 4.8.0
   */
  public static abstract class Converter<T> {
    /**
     * Converts a value of the specified type to an {@link LDValue}.
     * <p>
     * This method should never throw an exception; if for some reason the value is invalid,
     * it should return {@link LDValue#ofNull()}.
     * 
     * @param value a value of this type
     * @return an {@link LDValue}
     */
    public abstract LDValue fromType(T value);
    
    /**
     * Converts an {@link LDValue} to a value of the specified type.
     * <p>
     * This method should never throw an exception; if the conversion cannot be done, it should
     * return the default value of the given type (zero for numbers, null for nullable types).
     * 
     * @param value an {@link LDValue}
     * @return a value of this type
     */
    public abstract T toType(LDValue value);
    
    /**
     * Initializes an {@link LDValue} as an array, from a sequence of this type.
     * <p>
     * Values are copied, so subsequent changes to the source values do not affect the array.
     * <p>
     * Example:
     * <pre><code>
     *     List<Integer> listOfInts = ImmutableList.<Integer>builder().add(1).add(2).add(3).build();
     *     LDValue arrayValue = LDValue.Convert.Integer.arrayFrom(listOfInts);
     * </code></pre>
     * 
     * @param values a sequence of elements of the specified type
     * @return a value representing a JSON array, or {@link LDValue#ofNull()} if the parameter was null
     * @see LDValue#buildArray()
     */
    public LDValue arrayFrom(Iterable<T> values) {
      ArrayBuilder ab = LDValue.buildArray();
      for (T value: values) {
        ab.add(fromType(value));
      }
      return ab.build();
    }

    /**
     * Initializes an {@link LDValue} as an array, from a sequence of this type.
     * <p>
     * Values are copied, so subsequent changes to the source values do not affect the array.
     * <p>
     * Example:
     * <pre><code>
     *     LDValue arrayValue = LDValue.Convert.Integer.arrayOf(1, 2, 3);
     * </code></pre>
     * 
     * @param values a sequence of elements of the specified type
     * @return a value representing a JSON array, or {@link LDValue#ofNull()} if the parameter was null
     * @see LDValue#buildArray()
     */
    @SuppressWarnings("unchecked")
    public LDValue arrayOf(T... values) {
      ArrayBuilder ab = LDValue.buildArray();
      for (T value: values) {
        ab.add(fromType(value));
      }
      return ab.build();
    }
    
    /**
     * Initializes an {@link LDValue} as an object, from a map containing this type.
     * <p>
     * Values are copied, so subsequent changes to the source map do not affect the array.
     * <p>
     * Example:
     * <pre><code>
     *     Map<String, Integer> mapOfInts = ImmutableMap.<String, Integer>builder().put("a", 1).build();
     *     LDValue objectValue = LDValue.Convert.Integer.objectFrom(mapOfInts);
     * </code></pre>
     * 
     * @param map a map with string keys and values of the specified type
     * @return a value representing a JSON object, or {@link LDValue#ofNull()} if the parameter was null
     * @see LDValue#buildObject()
     */
    public LDValue objectFrom(Map<String, T> map) {
      ObjectBuilder ob = LDValue.buildObject();
      for (String key: map.keySet()) {
        ob.put(key, fromType(map.get(key)));
      }
      return ob.build();
    }
  }
  
  /**
   * Predefined instances of {@link LDValue.Converter} for commonly used types.
   * <p>
   * These are mostly useful for methods that convert {@link LDValue} to or from a collection of
   * some type, such as {@link LDValue.Converter#arrayOf(Object...)} and
   * {@link LDValue#valuesAs(Converter)}.
   * 
   * @since 4.8.0
   */
  public static abstract class Convert {
    private Convert() {}
    
    /**
     * A {@link LDValue.Converter} for booleans.
     */
    public static final Converter<java.lang.Boolean> Boolean = new Converter<java.lang.Boolean>() {
      public LDValue fromType(java.lang.Boolean value) {
        return value == null ? LDValue.ofNull() : LDValue.of(value.booleanValue());
      }
      public java.lang.Boolean toType(LDValue value) {
        return java.lang.Boolean.valueOf(value.booleanValue());
      }
    };
    
    /**
     * A {@link LDValue.Converter} for integers.
     */
    public static final Converter<java.lang.Integer> Integer = new Converter<java.lang.Integer>() {
      public LDValue fromType(java.lang.Integer value) {
        return value == null ? LDValue.ofNull() : LDValue.of(value.intValue());
      }
      public java.lang.Integer toType(LDValue value) {
        return java.lang.Integer.valueOf(value.intValue());
      }
    };
    
    /**
     * A {@link LDValue.Converter} for floats.
     */
    public static final Converter<java.lang.Float> Float = new Converter<java.lang.Float>() {
      public LDValue fromType(java.lang.Float value) {
        return value == null ? LDValue.ofNull() : LDValue.of(value.floatValue());
      }
      public java.lang.Float toType(LDValue value) {
        return java.lang.Float.valueOf(value.floatValue());
      }
    };
    
    /**
     * A {@link LDValue.Converter} for doubles.
     */
    public static final Converter<java.lang.Double> Double = new Converter<java.lang.Double>() {
      public LDValue fromType(java.lang.Double value) {
        return value == null ? LDValue.ofNull() : LDValue.of(value.doubleValue());
      }
      public java.lang.Double toType(LDValue value) {
        return java.lang.Double.valueOf(value.doubleValue());
      }
    };
    
    /**
     * A {@link LDValue.Converter} for strings.
     */
    public static final Converter<java.lang.String> String = new Converter<java.lang.String>() {
      public LDValue fromType(java.lang.String value) {
        return LDValue.of(value);
      }
      public java.lang.String toType(LDValue value) {
        return value.stringValue();
      }
    };
  }
}
