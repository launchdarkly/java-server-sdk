package com.launchdarkly.client;


import com.google.gson.JsonElement;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.List;

/**
 * Contains either a fixed variation or percent rollout to serve.
 * Invariant: one of the variation or rollout must be non-nil.
 */
class VariationOrRollout {
  private static final float long_scale = (float) 0xFFFFFFFFFFFFFFFL;

  private Integer variation;
  private Rollout rollout;

  // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
  VariationOrRollout() {}

  VariationOrRollout(Integer variation, Rollout rollout) {
    this.variation = variation;
    this.rollout = rollout;
  }

  Integer variationIndexForUser(LDUser user, String key, String salt) {
    if (variation != null) {
      return variation;
    } else if (rollout != null) {
      String bucketBy = rollout.bucketBy == null ? "key" : rollout.bucketBy;
      float bucket = bucketUser(user, key, bucketBy, salt);
      float sum = 0F;
      for (WeightedVariation wv : rollout.variations) {
        sum += (float) wv.weight / 100000F;
        if (bucket < sum) {
          return wv.variation;
        }
      }
    }
    return null;
  }

  private float bucketUser(LDUser user, String key, String attr, String salt) {
    JsonElement userValue = user.getValueForEvaluation(attr);
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
    return 0F;
  }

  static class Rollout {
    private List<WeightedVariation> variations;
    private String bucketBy;

    // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
    Rollout() {}

    Rollout(List<WeightedVariation> variations, String bucketBy) {
      this.variations = variations;
      this.bucketBy = bucketBy;
    }
  }

  static class WeightedVariation {
    private int variation;
    private int weight;

    // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
    WeightedVariation() {}

    WeightedVariation(int variation, int weight) {
      this.variation = variation;
      this.weight = weight;
    }
  }
}
