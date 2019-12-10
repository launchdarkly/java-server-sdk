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
  static Integer variationIndexForUser(FlagModel.VariationOrRollout vr, LDUser user, String key, String salt) {
    Integer variation = vr.getVariation();
    if (variation != null) {
      return variation;
    } else {
      FlagModel.Rollout rollout = vr.getRollout();
      if (rollout != null) {
        float bucket = bucketUser(user, key, rollout.getBucketBy(), salt);
        float sum = 0F;
        for (FlagModel.WeightedVariation wv : rollout.getVariations()) {
          sum += (float) wv.getWeight() / 100000F;
          if (bucket < sum) {
            return wv.getVariation();
          }
        }
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
