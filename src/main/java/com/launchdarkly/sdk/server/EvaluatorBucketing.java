package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * Encapsulates the logic for percentage rollouts.
 */
abstract class EvaluatorBucketing {
  private EvaluatorBucketing() {}
  
  private static final float LONG_SCALE = (float) 0xFFFFFFFFFFFFFFFL;

  static float computeBucketValue(
      Integer seed,
      LDContext context,
      String flagOrSegmentKey,
      AttributeRef attr,
      String salt
      ) {
    LDValue contextValue;
    if (attr == null) {
      contextValue = LDValue.of(context.getKey());
    } else {
      if (!attr.isValid()) {
        return 0;
      }
      contextValue = context.getValue(attr);
      if (contextValue.isNull()) {
        return 0;
      }
    }

    String idHash = getBucketableStringValue(contextValue);
    if (idHash == null) {
      return 0;
    }

    String prefix;
    if (seed != null) {
      prefix = seed.toString();
    } else {
      prefix = flagOrSegmentKey + "." + salt;
    }
    String secondary = context.getSecondary();
    if (secondary != null) {
      idHash = idHash + "." + secondary;
    }
    String hash = DigestUtils.sha1Hex(prefix + "." + idHash).substring(0, 15);
    long longVal = Long.parseLong(hash, 16);
    return (float) longVal / LONG_SCALE;
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
