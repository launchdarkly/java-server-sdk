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

  // Computes a bucket value for a rollout or experiment. If an error condition prevents
  // us from computing a valid bucket value, we return 0, which will cause the evaluator
  // to select the first bucket. A special case is if no context of the desired kind is
  // found, in which case we return the special value -1; this similarly will cause the
  // first bucket to be chosen (since it is less than the end value of the bucket, just
  // as 0 is), but also tells the evaluator that inExperiment must be set to false.
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
      return -1;
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
