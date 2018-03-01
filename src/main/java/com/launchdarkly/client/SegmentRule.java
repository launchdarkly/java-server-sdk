package com.launchdarkly.client;

import java.util.List;

public class SegmentRule {
  private final List<Clause> clauses;
  private final Integer weight;
  private final String bucketBy;
  
  public SegmentRule(List<Clause> clauses, Integer weight, String bucketBy) {
    this.clauses = clauses;
    this.weight = weight;
    this.bucketBy = bucketBy;
  }

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