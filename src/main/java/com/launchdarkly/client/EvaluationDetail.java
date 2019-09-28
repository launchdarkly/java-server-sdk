package com.launchdarkly.client;

import com.google.common.base.Objects;
import com.google.gson.JsonElement;
import com.launchdarkly.client.value.LDValue;

/**
 * An object returned by the "variation detail" methods such as {@link LDClientInterface#boolVariationDetail(String, LDUser, boolean)},
 * combining the result of a flag evaluation with an explanation of how it was calculated.
 * @param <T> the type of the wrapped value
 * @since 4.3.0
 */
public class EvaluationDetail<T> {

  private final EvaluationReason reason;
  private final Integer variationIndex;
  private final T value;
  private final LDValue jsonValue;

  /**
   * Constructs an instance without the {@code jsonValue} property.
   * 
   * @param reason an {@link EvaluationReason} (should not be null)
   * @param variationIndex an optional variation index
   * @param value a value of the desired type
   */
  @Deprecated
  public EvaluationDetail(EvaluationReason reason, Integer variationIndex, T value) {
    this.value = value;
    this.jsonValue = toLDValue(value);
    this.variationIndex = variationIndex;
    this.reason = reason;
  }

  /**
   * Constructs an instance with all properties specified.
   * 
   * @param reason an {@link EvaluationReason} (should not be null)
   * @param variationIndex an optional variation index
   * @param value a value of the desired type
   * @param jsonValue the {@link LDValue} representation of the value
   * @since 4.8.0
   */
  private EvaluationDetail(T value, LDValue jsonValue, Integer variationIndex, EvaluationReason reason) {
    this.value = value;
    this.jsonValue = jsonValue == null ? LDValue.ofNull() : jsonValue;
    this.variationIndex = variationIndex;
    this.reason = reason;
  }
  
  /**
   * Factory method for an arbitrary value.
   * 
   * @param value a value of the desired type
   * @param variationIndex an optional variation index
   * @param reason an {@link EvaluationReason} (should not be null)
   * @return an {@link EvaluationDetail}
   * @since 4.8.0
   */
  public static <T> EvaluationDetail<T> fromValue(T value, Integer variationIndex, EvaluationReason reason) {
    return new EvaluationDetail<>(value, toLDValue(value), variationIndex, reason);
  }
  
  /**
   * Factory method for using an {@link LDValue} as the value.
   * 
   * @param jsonValue a value
   * @param variationIndex an optional variation index
   * @param reason an {@link EvaluationReason} (should not be null)
   * @return an {@link EvaluationDetail}
   * @since 4.8.0
   */
  public static EvaluationDetail<LDValue> fromJsonValue(LDValue jsonValue, Integer variationIndex, EvaluationReason reason) {
    return new EvaluationDetail<>(jsonValue, jsonValue, variationIndex, reason);
  }

  /**
   * Factory method for an arbitrary value that also specifies it as a {@link LDValue}.
   * 
   * @param value a value of the desired type
   * @param jsonValue the same value represented as an {@link LDValue}
   * @param variationIndex an optional variation index
   * @param reason an {@link EvaluationReason} (should not be null)
   * @return an {@link EvaluationDetail}
   * @since 4.8.0
   */
  public static <T> EvaluationDetail<T> fromValueWithJsonValue(T value, LDValue jsonValue, Integer variationIndex, EvaluationReason reason) {
    return new EvaluationDetail<>(value, jsonValue, variationIndex, reason);
  }
  
  static EvaluationDetail<LDValue> error(EvaluationReason.ErrorKind errorKind, LDValue defaultValue) {
    return new EvaluationDetail<>(defaultValue == null ? LDValue.ofNull() : defaultValue, defaultValue, null, EvaluationReason.error(errorKind));
  }
  
  @SuppressWarnings("deprecation")
  private static LDValue toLDValue(Object value) {
    if (value == null) {
      return LDValue.ofNull();
    }
    if (value instanceof LDValue) {
      return (LDValue)value;
    }
    if (value instanceof JsonElement) {
      return LDValue.fromJsonElement((JsonElement)value);
    }
    if (value instanceof Boolean) {
      return LDValue.of(((Boolean)value).booleanValue());
    }
    if (value instanceof Integer) {
      return LDValue.of(((Integer)value).intValue());
    }
    if (value instanceof Long) {
      return LDValue.of(((Long)value).longValue());
    }
    if (value instanceof Float) {
      return LDValue.of(((Float)value).floatValue());
    }
    if (value instanceof Double) {
      return LDValue.of(((Double)value).doubleValue());
    }
    if (value instanceof String) {
      return LDValue.of((String)value);
    }
    return LDValue.ofNull();
  }
  
  /**
   * An object describing the main factor that influenced the flag evaluation value.
   * @return an {@link EvaluationReason}
   */
  public EvaluationReason getReason() {
    return reason;
  }

  /**
   * The index of the returned value within the flag's list of variations, e.g. 0 for the first variation -
   * or {@code null} if the default value was returned.
   * @return the variation index or null
   */
  public Integer getVariationIndex() {
    return variationIndex;
  }

  /**
   * The result of the flag evaluation. This will be either one of the flag's variations or the default
   * value that was passed to the {@code variation} method.
   * @return the flag value
   */
  public T getValue() {
    return value;
  }
  
  /**
   * The result of the flag evaluation as an {@link LDValue}. This will be either one of the flag's variations
   * or the default value that was passed to the {@code variation} method.
   * @return the flag value
   * @since 4.8.0
   */
  public LDValue getJsonValue() {
    return jsonValue;
  }
  
  /**
   * Returns true if the flag evaluation returned the default value, rather than one of the flag's
   * variations.
   * @return true if this is the default value
   */
  public boolean isDefaultValue() {
    return variationIndex == null;
  }
  
  @Override
  public boolean equals(Object other) {
    if (other instanceof EvaluationDetail) {
      @SuppressWarnings("unchecked")
      EvaluationDetail<T> o = (EvaluationDetail<T>)other;
      return Objects.equal(reason, o.reason) && Objects.equal(variationIndex, o.variationIndex) && Objects.equal(value, o.value)
          && Objects.equal(jsonValue, o.jsonValue);
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    return Objects.hashCode(reason, variationIndex, value, jsonValue);
  }
  
  @Override
  public String toString() {
    return "{" + reason + "," + variationIndex + "," + value + "}";
  }
}
