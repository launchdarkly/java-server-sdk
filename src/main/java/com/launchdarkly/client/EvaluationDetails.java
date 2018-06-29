package com.launchdarkly.client;

import com.google.common.base.Objects;
import com.google.gson.JsonElement;

/**
 * An object returned by the "variation detail" methods such as {@link LDClientInterface#boolVariationDetails(String, LDUser, boolean),
 * combining the result of a flag evaluation with an explanation of how it was calculated.
 * @since 4.3.0
 */
public class EvaluationDetails<T> {

  /**
   * Enum values used in {@link EvaluationDetails} to explain why a flag evaluated to a particular value.
   * @since 4.3.0
   */
  public static enum Reason {
    /**
     * Indicates that the flag was off and therefore returned its configured off value.
     */
    OFF,
    /**
     * Indicates that the user key was specifically targeted for this flag.
     */
    TARGET_MATCH,
    /**
     * Indicates that the user matched one of the flag's rules.
     */
    RULE_MATCH,
    /**
     * Indicates that the flag was treated as if it was off because it had a prerequisite flag that
     * either was off or did not return the expected variation.,
     */
    PREREQUISITE_FAILED,
    /**
     * Indicates that the flag was on but the user did not match any targets or rules. 
     */
    FALLTHROUGH,
    /**
     * Indicates that the default value (passed as a parameter to one of the {@code variation} methods0
     * was returned. This normally indicates an error condition. 
     */
    DEFAULT;
  }
  
  private final Reason reason;
  private final Integer variationIndex;
  private final T value;
  private final Integer matchIndex;
  private final String matchId;
  
  public EvaluationDetails(Reason reason, Integer variationIndex, T value, Integer matchIndex, String matchId) {
    super();
    this.reason = reason;
    this.variationIndex = variationIndex;
    this.value = value;
    this.matchIndex = matchIndex;
    this.matchId = matchId;
  }

  static EvaluationDetails<JsonElement> off(Integer offVariation, JsonElement value) {
    return new EvaluationDetails<JsonElement>(Reason.OFF, offVariation, value, null, null);
  }
  
  static EvaluationDetails<JsonElement> fallthrough(int variationIndex, JsonElement value) {
    return new EvaluationDetails<JsonElement>(Reason.FALLTHROUGH, variationIndex, value, null, null);
  }
  
  static <T> EvaluationDetails<T> defaultValue(T value) {
    return new EvaluationDetails<T>(Reason.DEFAULT, null, value, null, null);
  }
  
  /**
   * An enum describing the main factor that influenced the flag evaluation value.
   * @return a {@link Reason}
   */
  public Reason getReason() {
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
   * A number whose meaning depends on the {@link Reason}. For {@link Reason#TARGET_MATCH}, it is the
   * zero-based index of the matched target. For {@link Reason#RULE_MATCH}, it is the zero-based index
   * of the matched rule. For all other reasons, it is {@code null}.
   * @return the index of the matched item or null
   */
  public Integer getMatchIndex() {
    return matchIndex;
  }

  /**
   * A string whose meaning depends on the {@link Reason}. For {@link Reason#RULE_MATCH}, it is the
   * unique identifier of the matched rule, if any. For {@link Reason#PREREQUISITE_FAILED}, it is the
   * flag key of the prerequisite flag that stopped evaluation. For all other reasons, it is {@code null}.
   * @return a rule ID, flag key, or null
   */
  public String getMatchId() {
    return matchId;
  }
  
  @Override
  public boolean equals(Object other) {
    if (other instanceof EvaluationDetails) {
      @SuppressWarnings("unchecked")
      EvaluationDetails<T> o = (EvaluationDetails<T>)other;
      return reason == o.reason && variationIndex == o.variationIndex && Objects.equal(value, o.value)
          && matchIndex == o.matchIndex && Objects.equal(matchId, o.matchId);
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    return Objects.hashCode(reason, variationIndex, value, matchIndex, matchId);
  }
  
  @Override
  public String toString() {
    return "{" + reason + ", " + variationIndex + ", " + value + ", " + matchIndex + ", " + matchId + "}";
  }
}
