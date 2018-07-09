package com.launchdarkly.client;

import com.google.common.base.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An object returned by the "variation detail" methods such as {@link LDClientInterface#boolVariationDetails(String, LDUser, boolean),
 * combining the result of a flag evaluation with an explanation of how it was calculated.
 * @since 4.3.0
 */
public class EvaluationDetail<T> {

  private final EvaluationReason reason;
  private final Integer variationIndex;
  private final T value;
  
  public EvaluationDetail(EvaluationReason reason, Integer variationIndex, T value) {
    checkNotNull(reason);
    this.reason = reason;
    this.variationIndex = variationIndex;
    this.value = value;
  }
  
  static <T> EvaluationDetail<T> error(EvaluationReason.ErrorKind errorKind, T defaultValue) {
    return new EvaluationDetail<>(EvaluationReason.error(errorKind), null, defaultValue);
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
  
  @Override
  public boolean equals(Object other) {
    if (other instanceof EvaluationDetail) {
      @SuppressWarnings("unchecked")
      EvaluationDetail<T> o = (EvaluationDetail<T>)other;
      return Objects.equal(reason, o.reason) && variationIndex == o.variationIndex && Objects.equal(value, o.value);
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    return Objects.hashCode(reason, variationIndex, value);
  }
  
  @Override
  public String toString() {
    return "{" + reason + "," + variationIndex + "," + value + "}";
  }
}
