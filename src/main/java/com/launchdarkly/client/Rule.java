package com.launchdarkly.client;

import java.util.List;

/**
 * Expresses a set of AND-ed matching conditions for a user, along with either the fixed variation or percent rollout
 * to serve if the conditions match.
 * Invariant: one of the variation or rollout must be non-nil.
 */
class Rule extends VariationOrRollout {
  private List<Clause> clauses;

  // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
  Rule() {
    super();
  }

  Rule(List<Clause> clauses, Integer variation, Rollout rollout) {
    super(variation, rollout);
    this.clauses = clauses;
  }

  boolean matchesUser(LDUser user) {
    for (Clause clause : clauses) {
      if (!clause.matchesUser(user)) {
        return false;
      }
    }
    return true;
  }
}
