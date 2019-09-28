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
  private boolean trackEvents;

  // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
  Rule() {
    super();
  }

  Rule(String id, List<Clause> clauses, Integer variation, Rollout rollout, boolean trackEvents) {
    super(variation, rollout);
    this.id = id;
    this.clauses = clauses;
    this.trackEvents = trackEvents;
  }
  
  Rule(String id, List<Clause> clauses, Integer variation, Rollout rollout) {
    this(id, clauses, variation, rollout, false);
  }

  String getId() {
    return id;
  }
  
  Iterable<Clause> getClauses() {
    return clauses;
  }
  
  boolean isTrackEvents() {
    return trackEvents;
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