package com.launchdarkly.client;

import java.util.List;

/**
 * Expresses a set of AND-ed matching conditions for a user, along with either the fixed variation or percent rollout
 * to serve if the conditions match.
 * Invariant: one of the variation or rollout must be non-nil.
 */
class Rule extends VariationOrRollout {
  private String id;
  private List<Clause> clauses;

  // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
  Rule() {
    super();
  }

  Rule(String id, List<Clause> clauses, Integer variation, Rollout rollout) {
    super(variation, rollout);
    this.id = id;
    this.clauses = clauses;
  }

  String getId() {
    return id;
  }
  
  boolean matchesUser(FeatureStore store, LDUser user) {
    for (Clause clause : clauses) {
      if (!clause.matchesUser(store, user)) {
        return false;
      }
    }
    return true;
  }
}