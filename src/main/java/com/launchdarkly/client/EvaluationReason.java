package com.launchdarkly.client;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import java.util.Objects;

/**
 * Describes the reason that a flag evaluation produced a particular value. This is returned by
 * methods such as {@link LDClientInterface#boolVariationDetails(String, LDUser, boolean).
 * 
 * Note that this is an enum-like class hierarchy rather than an enum, because some of the
 * possible reasons have their own properties.
 * 
 * @since 4.3.0
 */
public abstract class EvaluationReason {

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
     * Indicates that the default value (passed as a parameter to one of the {@code variation} methods)
     * was returned. This normally indicates an error condition.
     */
    DEFAULT;
  }
  
  /**
   * Returns an enum indicating the general category of the reason.
   * @return a {@link Kind} value
   */
  public abstract Kind getKind();
  
  @Override
  public String toString() {
    return getKind().name();
  }

  private EvaluationReason() { }
  
  /**
   * Returns an instance of {@link Off}.
   */
  public static Off off() {
    return Off.instance;
  }
  
  /**
   * Returns an instance of {@link TargetMatch}.
   */
  public static TargetMatch targetMatch(int targetIndex) {
    return new TargetMatch(targetIndex);
  }
  
  /**
   * Returns an instance of {@link RuleMatch}.
   */
  public static RuleMatch ruleMatch(int ruleIndex, String ruleId) {
    return new RuleMatch(ruleIndex, ruleId);
  }
  
  /**
   * Returns an instance of {@link PrerequisitesFailed}.
   */
  public static PrerequisitesFailed prerequisitesFailed(Iterable<String> prerequisiteKeys) {
    return new PrerequisitesFailed(prerequisiteKeys);
  }
  
  /**
   * Returns an instance of {@link Fallthrough}.
   */
  public static Fallthrough fallthrough() {
    return Fallthrough.instance;
  }
  
  /**
   * Returns an instance of {@link Default}.
   */
  public static Default defaultValue() {
    return Default.instance;
  }
  
  /**
   * Subclass of {@link EvaluationReason} that indicates that the flag was off and therefore returned
   * its configured off value.
   */
  public static class Off extends EvaluationReason {
    public Kind getKind() {
      return Kind.OFF;
    }
    
    private static final Off instance = new Off();
  }
  
  /**
   * Subclass of {@link EvaluationReason} that indicates that the user key was specifically targeted
   * for this flag.
   */
  public static class TargetMatch extends EvaluationReason {
    private final int targetIndex;
    
    private TargetMatch(int targetIndex) {
      this.targetIndex = targetIndex;
    }
    
    public Kind getKind() {
      return Kind.TARGET_MATCH;
    }
    
    public int getTargetIndex() {
      return targetIndex;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof TargetMatch) {
        TargetMatch o = (TargetMatch)other;
        return targetIndex == o.targetIndex;
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return targetIndex;
    }
    
    @Override
    public String toString() {
      return getKind().name() + "(" + targetIndex + ")";
    }
  }
  
  /**
   * Subclass of {@link EvaluationReason} that indicates that the user matched one of the flag's rules.
   */
  public static class RuleMatch extends EvaluationReason {
    private final int ruleIndex;
    private final String ruleId;
    
    private RuleMatch(int ruleIndex, String ruleId) {
      this.ruleIndex = ruleIndex;
      this.ruleId = ruleId;
    }
    
    public Kind getKind() {
      return Kind.RULE_MATCH;
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
        return ruleIndex == o.ruleIndex && Objects.equals(ruleId,  o.ruleId);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return Objects.hash(ruleIndex, ruleId);
    }
    
    @Override
    public String toString() {
      return getKind().name() + "(" + (ruleId == null ? String.valueOf(ruleIndex) : ruleId + ")");
    }
  }
  
  /**
   * Subclass of {@link EvaluationReason} that indicates that the flag was considered off because it
   * had at least one prerequisite flag that either was off or did not return the desired variation.
   */
  public static class PrerequisitesFailed extends EvaluationReason {
    private final ImmutableList<String> prerequisiteKeys;
    
    private PrerequisitesFailed(Iterable<String> prerequisiteKeys) {
      this.prerequisiteKeys = ImmutableList.copyOf(prerequisiteKeys);
    }
    
    public Kind getKind() {
      return Kind.PREREQUISITES_FAILED;
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
   */
  public static class Fallthrough extends EvaluationReason {
    public Kind getKind() {
      return Kind.FALLTHROUGH;
    }
    
    private static final Fallthrough instance = new Fallthrough();
  }
  
  /**
   * Subclass of {@link EvaluationReason} that indicates that the default value (passed as a parameter
   * to one of the {@code variation} methods) was returned. This normally indicates an error condition.
   */
  public static class Default extends EvaluationReason {
    public Kind getKind() {
      return Kind.DEFAULT;
    }
    
    private static final Default instance = new Default();
  }
}
