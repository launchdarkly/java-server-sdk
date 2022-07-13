package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * Encapsulates the logic for percentage rollouts.
 */
abstract class EvaluatorBucketing {
  private EvaluatorBucketing() {}
  
  private static final float LONG_SCALE = (float) 0xFFFFFFFFFFFFFFFL;

  static float bucketUser(Integer seed, LDUser user, String key, AttributeRef attr, String salt) {
    LDValue userValue = user.getAttribute(attr == null ? UserAttribute.KEY : UserAttribute.forName(attr.toString()));
    String idHash = getBucketableStringValue(userValue);
    if (idHash != null) {
      String prefix;
      if (seed != null) {
        prefix = seed.toString();
      } else {
        prefix = key + "." + salt;
      }
      if (user.getSecondary() != null) {
        idHash = idHash + "." + user.getSecondary();
      }
      String hash = DigestUtils.sha1Hex(prefix + "." + idHash).substring(0, 15);
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
