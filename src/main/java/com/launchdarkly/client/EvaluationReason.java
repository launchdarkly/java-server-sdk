package com.launchdarkly.client;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Describes the reason that a flag evaluation produced a particular value. This is returned by
 * methods such as {@link LDClientInterface#boolVariationDetail(String, LDUser, boolean)}.
 * 
 * Note that this is an enum-like class hierarchy rather than an enum, because some of the
 * possible reasons have their own properties.
 * 
 * @since 4.3.0
 */
public abstract class EvaluationReason {

  /**
   * Enumerated type defining the possible values of {@link EvaluationReason#getKind()}.
   * @since 4.3.0
   */
  public static enum Kind {
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
     * Indicates that the flag was considered off because it had at least one prerequisite flag
     * that either was off or did not return the desired variation.
     */
    PREREQUISITES_FAILED,
    /**
     * Indicates that the flag was on but the user did not match any targets or rules. 
     */
    FALLTHROUGH,
    /**
     * Indicates that the flag could not be evaluated, e.g. because it does not exist or due to an unexpected
     * error. In this case the result value will be the default value that the caller passed to the client.
     */
    ERROR;
  }
  
  /**
   * Enumerated type defining the possible values of {@link EvaluationReason.Error#getErrorKind()}.
   * @since 4.3.0
   */
  public static enum ErrorKind {
    /**
     * Indicates that the caller tried to evaluate a flag before the client had successfully initialized.
     */
    CLIENT_NOT_READY,
    /**
     * Indicates that the caller provided a flag key that did not match any known flag.
     */
    FLAG_NOT_FOUND,
    /**
     * Indicates that the caller passed {@code null} for the user parameter, or the user lacked a key.
     */
    USER_NOT_SPECIFIED,
    /**
     * Indicates that the result value was not of the requested type, e.g. you called
     * {@link LDClientInterface#boolVariationDetail(String, LDUser, boolean)} but the value was an integer.
     */
    WRONG_TYPE,
    /**
     * Indicates that an unexpected exception stopped flag evaluation; check the log for details.
     */
    EXCEPTION
  }
  
  private final Kind kind;
  
  /**
   * Returns an enum indicating the general category of the reason.
   * @return a {@link Kind} value
   */
  public Kind getKind()
  {
    return kind;
  }
  
  @Override
  public String toString() {
    return getKind().name();
  }

  protected EvaluationReason(Kind kind)
  {
    this.kind = kind;
  }
  
  /**
   * Returns an instance of {@link Off}.
   * @return a reason object
   */
  public static Off off() {
    return Off.instance;
  }
  
  /**
   * Returns an instance of {@link TargetMatch}.
   * @return a reason object
   */
  public static TargetMatch targetMatch() {
    return TargetMatch.instance;
  }
  
  /**
   * Returns an instance of {@link RuleMatch}.
   * @param ruleIndex the rule index
   * @param ruleId the rule identifier
   * @return a reason object
   */
  public static RuleMatch ruleMatch(int ruleIndex, String ruleId) {
    return new RuleMatch(ruleIndex, ruleId);
  }
  
  /**
   * Returns an instance of {@link PrerequisitesFailed}.
   * @param prerequisiteKeys the list of flag keys of prerequisites that failed 
   * @return a reason object
   */
  public static PrerequisitesFailed prerequisitesFailed(Iterable<String> prerequisiteKeys) {
    return new PrerequisitesFailed(prerequisiteKeys);
  }
  
  /**
   * Returns an instance of {@link Fallthrough}.
   * @return a reason object
   */
  public static Fallthrough fallthrough() {
    return Fallthrough.instance;
  }
  
  /**
   * Returns an instance of {@link Error}.
   * @param errorKind describes the type of error
   * @return a reason object
   */
  public static Error error(ErrorKind errorKind) {
    return new Error(errorKind);
  }
  
  /**
   * Subclass of {@link EvaluationReason} that indicates that the flag was off and therefore returned
   * its configured off value.
   * @since 4.3.0
   */
  public static class Off extends EvaluationReason {
    private Off() {
      super(Kind.OFF);
    }
    
    private static final Off instance = new Off();
  }
  
  /**
   * Subclass of {@link EvaluationReason} that indicates that the user key was specifically targeted
   * for this flag.
   * @since 4.3.0
   */
  public static class TargetMatch extends EvaluationReason {
    private TargetMatch()
    {
      super(Kind.TARGET_MATCH);
    }
    
    private static final TargetMatch instance = new TargetMatch();
  }
  
  /**
   * Subclass of {@link EvaluationReason} that indicates that the user matched one of the flag's rules.
   * @since 4.3.0
   */
  public static class RuleMatch extends EvaluationReason {
    private final int ruleIndex;
    private final String ruleId;
    
    private RuleMatch(int ruleIndex, String ruleId) {
      super(Kind.RULE_MATCH);
      this.ruleIndex = ruleIndex;
      this.ruleId = ruleId;
    }
    
    public int getRuleIndex() {
      return ruleIndex;
    }
    
    public String getRuleId() {
      return ruleId;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof RuleMatch) {
        RuleMatch o = (RuleMatch)other;
        return ruleIndex == o.ruleIndex && Objects.equals(ruleId, o.ruleId);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return Objects.hash(ruleIndex, ruleId);
    }
    
    @Override
    public String toString() {
      return getKind().name() + "(" + ruleIndex + (ruleId == null ? "" : ("," + ruleId)) + ")";
    }
  }
  
  /**
   * Subclass of {@link EvaluationReason} that indicates that the flag was considered off because it
   * had at least one prerequisite flag that either was off or did not return the desired variation.
   * @since 4.3.0
   */
  public static class PrerequisitesFailed extends EvaluationReason {
    private final ImmutableList<String> prerequisiteKeys;
    
    private PrerequisitesFailed(Iterable<String> prerequisiteKeys) {
      super(Kind.PREREQUISITES_FAILED);
      checkNotNull(prerequisiteKeys);
      this.prerequisiteKeys = ImmutableList.copyOf(prerequisiteKeys);
    }
    
    public Iterable<String> getPrerequisiteKeys() {
      return prerequisiteKeys;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof PrerequisitesFailed) {
        PrerequisitesFailed o = (PrerequisitesFailed)other;
        return prerequisiteKeys.equals(o.prerequisiteKeys);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return prerequisiteKeys.hashCode();
    }
    
    @Override
    public String toString() {
      return getKind().name() + "(" + Joiner.on(",").join(prerequisiteKeys) + ")";
    }
  }
  
  /**
   * Subclass of {@link EvaluationReason} that indicates that the flag was on but the user did not
   * match any targets or rules.
   * @since 4.3.0
   */
  public static class Fallthrough extends EvaluationReason {
    private Fallthrough()
    {
      super(Kind.FALLTHROUGH);
    }
    
    private static final Fallthrough instance = new Fallthrough();
  }
  
  /**
   * Subclass of {@link EvaluationReason} that indicates that the flag could not be evaluated.
   * @since 4.3.0
   */
  public static class Error extends EvaluationReason {
    private final ErrorKind errorKind;
    
    private Error(ErrorKind errorKind) {
      super(Kind.ERROR);
      checkNotNull(errorKind);
      this.errorKind = errorKind;
    }
    
    public ErrorKind getErrorKind() {
      return errorKind;
    }
    
    @Override
    public boolean equals(Object other) {
      return other instanceof Error && errorKind == ((Error) other).errorKind;
    }
    
    @Override
    public int hashCode() {
      return errorKind.hashCode();
    }
    
    @Override
    public String toString() {
      return getKind().name() + "(" + errorKind.name() + ")";
    }
  }
}
