package com.launchdarkly.client;

import java.util.List;

/**
 * Internal data model class.
 * 
 * @deprecated This class was made public in error and will be removed in a future release. It is used internally by the SDK.
 */
@Deprecated
public class SegmentRule {
  private final List<Clause> clauses;
  private final Integer weight;
  private final String bucketBy;
  
  /**
   * Used internally to construct an instance.
   * @param clauses the clauses in the rule
   * @param weight the rollout weight
   * @param bucketBy the attribute for computing a rollout
   */
  public SegmentRule(List<Clause> clauses, Integer weight, String bucketBy) {
    this.clauses = clauses;
    this.weight = weight;
    this.bucketBy = bucketBy;
  }

  /**
   * Used internally to match a user against a segment.
   * @param user the user to match
   * @param segmentKey the segment key
   * @param salt the segment's salt string
   * @return true if the user matches
   */
  public boolean matchUser(LDUser user, String segmentKey, String salt) {
    for (Clause c: clauses) {
      if (!c.matchesUserNoSegments(user)) {
        return false;
      }
    }
    
    // If the Weight is absent, this rule matches
    if (weight == null) {
      return true;
    }
    
    // All of the clauses are met. See if the user buckets in
    String by = (bucketBy == null) ? "key" : bucketBy;
    double bucket = VariationOrRollout.bucketUser(user, segmentKey, by, salt);
    double weight = (double)this.weight / 100000.0;
    return bucket < weight;
  }
}