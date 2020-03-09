package com.launchdarkly.sdk;

/**
 * Describes the type of an {@link LDValue}. These correspond to the standard types in JSON.
 * 
 * @since 4.8.0
 */
public enum LDValueType {
  /**
   * The value is null.
   */
  NULL,
  /**
   * The value is a boolean.
   */
  BOOLEAN,
  /**
   * The value is numeric. JSON does not have separate types for integers and floating-point values,
   * but you can convert to either.
   */
  NUMBER,
  /**
   * The value is a string.
   */
  STRING,
  /**
   * The value is an array.
   */
  ARRAY,
  /**
   * The value is an object (map).
   */
  OBJECT
}
