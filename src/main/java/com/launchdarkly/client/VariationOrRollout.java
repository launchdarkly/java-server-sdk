package com.launchdarkly.client;


import com.launchdarkly.client.value.LDValue;

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

  Integer getVariation() {
    return variation;
  }
  
  Rollout getRollout() {
    return rollout;
  }
  
  // Attempt to determine the variation index for a given user. Returns null if no index can be computed
  // due to internal inconsistency of the data (i.e. a malformed flag). 
  Integer variationIndexForUser(LDUser user, String key, String salt) {
    if (variation != null) {
      return variation;
    } else if (rollout != null && rollout.variations != null && !rollout.variations.isEmpty()) {
      String bucketBy = rollout.bucketBy == null ? "key" : rollout.bucketBy;
      float bucket = bucketUser(user, key, bucketBy, salt);
      float sum = 0F;
      for (WeightedVariation wv : rollout.variations) {
        sum += (float) wv.weight / 100000F;
        if (bucket < sum) {
          return wv.variation;
        }
      }
      // The user's bucket value was greater than or equal to the end of the last bucket. This could happen due
      // to a rounding error, or due to the fact that we are scaling to 100000 rather than 99999, or the flag
      // data could contain buckets that don't actually add up to 100000. Rather than returning an error in
      // this case (or changing the scaling, which would potentially change the results for *all* users), we
      // will simply put the user in the last bucket.
      return rollout.variations.get(rollout.variations.size() - 1).variation;
    }
    return null;
  }

  static float bucketUser(LDUser user, String key, String attr, String salt) {
    LDValue userValue = user.getValueForEvaluation(attr);
    String idHash = getBucketableStringValue(userValue);
    if (idHash != null) {
      if (!user.getSecondary().isNull()) {
        idHash = idHash + "." + user.getSecondary().stringValue();
      }
      String hash = DigestUtils.sha1Hex(key + "." + salt + "." + idHash).substring(0, 15);
      long longVal = Long.parseLong(hash, 16);
      return (float) longVal / long_scale;
    }
    return 0F;
  }

  private static String getBucketableStringValue(LDValue userValue) {
    switch (userValue.getType()) { 
    case STRING:
      return userValue.stringValue();
    case NUMBER:
      return userValue.isInt() ? String.valueOf(userValue.intValue()) : null;
    default:
      return null;
    }
  }
  
  static final class Rollout {
    private List<WeightedVariation> variations;
    private String bucketBy;

    // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
    Rollout() {}

    Rollout(List<WeightedVariation> variations, String bucketBy) {
      this.variations = variations;
      this.bucketBy = bucketBy;
    }
    
    List<WeightedVariation> getVariations() {
      return variations;
    }
    
    String getBucketBy() {
      return bucketBy;
    }
  }

  static final class WeightedVariation {
    private int variation;
    private int weight;

    // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
    WeightedVariation() {}

    WeightedVariation(int variation, int weight) {
      this.variation = variation;
      this.weight = weight;
    }
    
    int getVariation() {
      return variation;
    }
    
    int getWeight() {
      return weight;
    }
  }
}
