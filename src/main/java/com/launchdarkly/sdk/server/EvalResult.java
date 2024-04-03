package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.ErrorKind;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;

/**
 * Internal container for the results of an evaluation. This consists of:
 * <ul>
 * <li> an {@link EvaluationDetail} in a type-agnostic form using {@link LDValue}
 * <li> if appropriate, an additional precomputed {@link EvaluationDetail} for specific Java types
 * such as Boolean, so that calling a method like boolVariationDetail won't always have to create
 * a new instance
 * <li> the boolean forceReasonTracking property (see isForceReasonTracking)
 */
final class EvalResult {
  private static final EvaluationDetail<Boolean> WRONG_TYPE_BOOLEAN = wrongTypeWithValue(false);
  private static final EvaluationDetail<Integer> WRONG_TYPE_INTEGER = wrongTypeWithValue((int)0);
  private static final EvaluationDetail<Double> WRONG_TYPE_DOUBLE = wrongTypeWithValue((double)0);
  private static final EvaluationDetail<String> WRONG_TYPE_STRING = wrongTypeWithValue((String)null);
  
  private final EvaluationDetail<LDValue> anyType;
  private final EvaluationDetail<Boolean> asBoolean;
  private final EvaluationDetail<Integer> asInteger;
  private final EvaluationDetail<Double> asDouble;
  private final EvaluationDetail<String> asString;
  private final boolean forceReasonTracking;

  /**
   * Constructs an instance that wraps the specified EvaluationDetail and also precomputes
   * any appropriate type-specific variants (asBoolean, etc.).
   * 
   * @param original the original value
   * @return an EvaluatorResult
   */
  static EvalResult of(EvaluationDetail<LDValue> original) {
    return new EvalResult(original);
  }

  /**
   * Same as {@link #of(EvaluationDetail)} but specifies the individual properties.
   * 
   * @param value the value
   * @param variationIndex the variation index
   * @param reason the evaluation reason
   * @return an EvaluatorResult
   */
  static EvalResult of(LDValue value, int variationIndex, EvaluationReason reason) {
    return of(EvaluationDetail.fromValue(value, variationIndex, reason));
  }
  
  /**
   * Constructs an instance for an error result. The value is always null in this case because
   * this is a generalized result that wasn't produced by an individual variation() call, so
   * we do not know what the application might specify as a default value.
   * 
   * @param errorKind the error kind
   * @return an instance
   */
  static EvalResult error(ErrorKind errorKind) {
    return of(LDValue.ofNull(), EvaluationDetail.NO_VARIATION, EvaluationReason.error(errorKind));
  }

  static EvalResult error(ErrorKind errorKind, LDValue defaultValue) {
    return of(defaultValue, EvaluationDetail.NO_VARIATION, EvaluationReason.error(errorKind));
  }
  
  private EvalResult(EvaluationDetail<LDValue> original) {
    this.anyType = original.getValue() == null ?
        EvaluationDetail.fromValue(LDValue.ofNull(), original.getVariationIndex(), original.getReason()) :
        original;
    this.forceReasonTracking = original.getReason().isInExperiment();
        
    LDValue value = anyType.getValue();
    int index = anyType.getVariationIndex();
    EvaluationReason reason = anyType.getReason();
    
    this.asBoolean = value.getType() == LDValueType.BOOLEAN ?
        EvaluationDetail.fromValue(Boolean.valueOf(value.booleanValue()), index, reason) :
        WRONG_TYPE_BOOLEAN;
    this.asInteger = value.isNumber() ?
        EvaluationDetail.fromValue(Integer.valueOf(value.intValue()), index, reason) :
        WRONG_TYPE_INTEGER;
    this.asDouble = value.isNumber() ?
        EvaluationDetail.fromValue(Double.valueOf(value.doubleValue()), index, reason) :
        WRONG_TYPE_DOUBLE;
    this.asString = value.isString() || value.isNull() ?
        EvaluationDetail.fromValue(value.stringValue(), index, reason) :
        WRONG_TYPE_STRING;
  }
  
  private EvalResult(EvalResult from, EvaluationReason newReason) {
    this.anyType = transformReason(from.anyType, newReason);
    this.asBoolean = transformReason(from.asBoolean, newReason);
    this.asInteger = transformReason(from.asInteger, newReason);
    this.asDouble = transformReason(from.asDouble, newReason);
    this.asString = transformReason(from.asString, newReason);
    this.forceReasonTracking = from.forceReasonTracking;
  }
  
  private EvalResult(EvalResult from, boolean newForceTracking) {
    this.anyType = from.anyType;
    this.asBoolean = from.asBoolean;
    this.asInteger = from.asInteger;
    this.asDouble = from.asDouble;
    this.asString = from.asString;
    this.forceReasonTracking = newForceTracking;
  }
  
  /**
   * Returns the result as an {@code EvaluationDetail} where the value is an {@code LDValue},
   * allowing it to be of any JSON type.
   *  
   * @return the result properties
   */
  public EvaluationDetail<LDValue> getAnyType() {
    return anyType;
  }
  
  /**
   * Returns the result as an {@code EvaluationDetail} where the value is a {@code Boolean}.
   * If the result was not a boolean, the returned object has a value of false and a reason
   * that is a {@code WRONG_TYPE} error.
   * <p>
   * Note: the "wrong type" logic is just a safety measure to ensure that we never return
   * null. Normally, the result will already have been transformed by LDClient.evaluateInternal
   * if the wrong type was requested.
   * 
   * @return the result properties
   */
  public EvaluationDetail<Boolean> getAsBoolean() {
    return asBoolean;
  }
  
  /**
   * Returns the result as an {@code EvaluationDetail} where the value is an {@code Integer}.
   * If the result was not a number, the returned object has a value of zero and a reason
   * that is a {@code WRONG_TYPE} error (see {@link #getAsBoolean()}).
   * 
   * @return the result properties
   */
  public EvaluationDetail<Integer> getAsInteger() {
    return asInteger;
  }
  
  /**
   * Returns the result as an {@code EvaluationDetail} where the value is a {@code Double}.
   * If the result was not a number, the returned object has a value of zero and a reason
   * that is a {@code WRONG_TYPE} error (see {@link #getAsBoolean()}).
   * 
   * @return the result properties
   */
  public EvaluationDetail<Double> getAsDouble() {
    return asDouble;
  }
  
  /**
   * Returns the result as an {@code EvaluationDetail} where the value is a {@code String}.
   * If the result was not a string, the returned object has a value of {@code null} and a
   * reason that is a {@code WRONG_TYPE} error (see {@link #getAsBoolean()}).
   * 
   * @return the result properties
   */
  public EvaluationDetail<String> getAsString() {
    return asString;
  }
  
  /**
   * Returns the result value, which may be of any JSON type.
   * @return the result value
   */
  public LDValue getValue() { return anyType.getValue(); }
  
  /**
   * Returns the variation index, or {@link EvaluationDetail#NO_VARIATION} if evaluation failed
   * @return the variation index or {@link EvaluationDetail#NO_VARIATION}
   */
  public int getVariationIndex() { return anyType.getVariationIndex(); }
  
  /**
   * Returns the evaluation reason. This is never null, even though we may not always put the
   * reason into events.
   * @return the evaluation reason
   */
  public EvaluationReason getReason() { return anyType.getReason(); }
  
  /**
   * Returns true if the variation index is {@link EvaluationDetail#NO_VARIATION}, indicating
   * that evaluation failed or at least that no variation was selected.
   * @return true if there is no variation
   */
  public boolean isNoVariation() { return anyType.isDefaultValue(); }
  
  /**
   * Returns true if we need to send an evaluation reason in event data whenever we get this
   * result. This is true if any of the following are true: 1. the evaluation reason's
   * inExperiment property was true, which can happen if the evaluation involved a rollout
   * or experiment; 2. the evaluation reason was FALLTHROUGH, and the flag's trackEventsFallthrough
   * property was true; 3. the evaluation reason was RULE_MATCH, and the rule-level trackEvents
   * property was true. The consequence is that we will tell the event processor "definitely send
   * a individual event for this evaluation, even if the flag-level trackEvents was not true",
   * and also we will include the evaluation reason in the event even if the application did not
   * call a VariationDetail method.
   * @return true if reason tracking is required for this result
   */
  public boolean isForceReasonTracking() { return forceReasonTracking; }
  
  /**
   * Returns a transformed copy of this EvalResult with a different evaluation reason.
   * @param newReason the new evaluation reason
   * @return a transformed copy
   */
  public EvalResult withReason(EvaluationReason newReason) {
    return newReason.equals(this.anyType.getReason()) ? this : new EvalResult(this, newReason);
  }
  
  /**
   * Returns a transformed copy of this EvalResult with a different value for {@link #isForceReasonTracking()}.
   * @param newValue the new value for the property
   * @return a transformed copy
   */
  public EvalResult withForceReasonTracking(boolean newValue) {
    return this.forceReasonTracking == newValue ? this : new EvalResult(this, newValue);
  }
  
  @Override
  public boolean equals(Object other) {
    if (other instanceof EvalResult) {
      EvalResult o = (EvalResult)other;
      return anyType.equals(o.anyType) && forceReasonTracking == o.forceReasonTracking;
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    return anyType.hashCode() + (forceReasonTracking ? 1 : 0);
  }
  
  @Override
  public String toString() {
    if (forceReasonTracking) {
      return anyType.toString() + "(forceReasonTracking=true)";
    }
    return anyType.toString();
  }
  
  private static <T> EvaluationDetail<T> transformReason(EvaluationDetail<T> from, EvaluationReason newReason) {
    return from == null ? null :
      EvaluationDetail.fromValue(from.getValue(), from.getVariationIndex(), newReason);
  }
  
  private static <T> EvaluationDetail<T> wrongTypeWithValue(T value) {
    return EvaluationDetail.fromValue(value, NO_VARIATION, EvaluationReason.error(ErrorKind.WRONG_TYPE));
  }
}
