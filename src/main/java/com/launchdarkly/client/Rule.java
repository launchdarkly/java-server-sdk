package com.launchdarkly.client;

import com.google.gson.JsonElement;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.List;

import static com.launchdarkly.client.Clause.valueOf;

/**
 * Expresses a set of AND-ed matching conditions for a user, along with either the fixed variation or percent rollout
 * to serve if the conditions match.
 * Invariant: one of the variation or rollout must be non-nil.
 */
class Rule {
  private static final float long_scale = (float) 0xFFFFFFFFFFFFFFFL;

  private final List<Clause> clauses;
  private final Integer variation;
  private final Rollout rollout;

  Rule(List<Clause> clauses, Integer variation, Rollout rollout) {
    this.clauses = clauses;
    this.variation = variation;
    this.rollout = rollout;
  }

  boolean matchesUser(LDUser user) {
    for (Clause clause : clauses) {
      if (!clause.matchesUser(user)) {
        return false;
      }
    }
    return true;
  }

  Integer variationIndexForUser(LDUser user, String key, String salt) {
    if (variation != null) {
      return variation;
    } else if (rollout != null) {
      String bucketBy = rollout.bucketBy == null ? "key" : rollout.bucketBy;
      Float bucket = bucketUser(user, key, bucketBy, salt);
      Float sum = 0F;
      for (WeightedVariation wv : rollout.variations) {
        sum += (float) wv.weight / 100000F;
        if (bucket < sum) {
          return wv.variation;
        }
      }
    }
    return null;
  }

  private Float bucketUser(LDUser user, String key, String attr, String salt) {
    JsonElement userValue = valueOf(user, attr);
    String idHash;
    if (userValue != null) {
      if (userValue.isJsonPrimitive() && userValue.getAsJsonPrimitive().isString()) {
        idHash = userValue.getAsString();
        if (user.getSecondary() != null) {
          idHash = idHash + "." + user.getSecondary().getAsString();
        }
        String hash = DigestUtils.sha1Hex(key + "." + salt + "." + idHash).substring(0, 15);
        long longVal = Long.parseLong(hash, 16);
        return (float) longVal / long_scale;
      }
    }
    return null;
  }

  static class Rollout {
    private final List<WeightedVariation> variations;
    private final String bucketBy;

    Rollout(List<WeightedVariation> variations, String bucketBy) {
      this.variations = variations;
      this.bucketBy = bucketBy;
    }
  }

  static class WeightedVariation {
    private final int variation;
    private final int weight;

    WeightedVariation(int variation, int weight) {
      this.variation = variation;
      this.weight = weight;
    }
  }
}
