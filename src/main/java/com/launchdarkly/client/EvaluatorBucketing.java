package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * Encapsulates the logic for percentage rollouts.
 */
abstract class EvaluatorBucketing {
  private static final float LONG_SCALE = (float) 0xFFFFFFFFFFFFFFFL;

  // Attempt to determine the variation index for a given user. Returns null if no index can be computed
  // due to internal inconsistency of the data (i.e. a malformed flag). 
  static Integer variationIndexForUser(DataModel.VariationOrRollout vr, LDUser user, String key, String salt) {
    Integer variation = vr.getVariation();
    if (variation != null) {
      return variation;
    } else {
      DataModel.Rollout rollout = vr.getRollout();
      if (rollout != null && rollout.getVariations() != null && !rollout.getVariations().isEmpty()) {
        float bucket = bucketUser(user, key, rollout.getBucketBy(), salt);
        float sum = 0F;
        for (DataModel.WeightedVariation wv : rollout.getVariations()) {
          sum += (float) wv.getWeight() / 100000F;
          if (bucket < sum) {
            return wv.getVariation();
          }
        }
        // The user's bucket value was greater than or equal to the end of the last bucket. This could happen due
        // to a rounding error, or due to the fact that we are scaling to 100000 rather than 99999, or the flag
        // data could contain buckets that don't actually add up to 100000. Rather than returning an error in
        // this case (or changing the scaling, which would potentially change the results for *all* users), we
        // will simply put the user in the last bucket.
        return rollout.getVariations().get(rollout.getVariations().size() - 1).getVariation();
      }
    }
    return null;
  }

  static float bucketUser(LDUser user, String key, String attr, String salt) {
    LDValue userValue = user.getValueForEvaluation(attr == null ? "key" : attr);
    String idHash = getBucketableStringValue(userValue);
    if (idHash != null) {
      if (!user.getSecondary().isNull()) {
        idHash = idHash + "." + user.getSecondary().stringValue();
      }
      String hash = DigestUtils.sha1Hex(key + "." + salt + "." + idHash).substring(0, 15);
      long longVal = Long.parseLong(hash, 16);
      return (float) longVal / LONG_SCALE;
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
}
