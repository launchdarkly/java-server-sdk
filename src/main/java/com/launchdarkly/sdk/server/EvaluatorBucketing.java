package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.ContextKind;
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
      boolean isExperiment,
      Integer seed,
      LDContext context,
      ContextKind contextKind,
      String flagOrSegmentKey,
      AttributeRef attr,
      String salt
      ) {
    LDContext matchContext = context.getIndividualContext(contextKind);
    if (matchContext == null) {
      return 0;
    }
    LDValue contextValue;
    if (isExperiment || attr == null) {
      contextValue = LDValue.of(matchContext.getKey());
    } else {
      if (!attr.isValid()) {
        return 0;
      }
      contextValue = matchContext.getValue(attr);
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
    if (!isExperiment) { // secondary key is not supported in experiments
      String secondary = context.getSecondary();
      if (secondary != null) {
        idHash = idHash + "." + secondary;
      }
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
