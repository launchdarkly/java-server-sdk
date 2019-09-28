package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;
import com.launchdarkly.client.value.LDValueType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;

class Clause {
  private final static Logger logger = LoggerFactory.getLogger(Clause.class);

  private String attribute;
  private Operator op;
  private List<LDValue> values; //interpreted as an OR of values
  private boolean negate;

  public Clause() {
  }
  
  public Clause(String attribute, Operator op, List<LDValue> values, boolean negate) {
    this.attribute = attribute;
    this.op = op;
    this.values = values;
    this.negate = negate;
  }

  boolean matchesUserNoSegments(LDUser user) {
    LDValue userValue = user.getValueForEvaluation(attribute);
    if (userValue.isNull()) {
      return false;
    }

    if (userValue.getType() == LDValueType.ARRAY) {
      for (LDValue value: userValue.values()) {
        if (value.getType() == LDValueType.ARRAY || value.getType() == LDValueType.OBJECT) {
          logger.error("Invalid custom attribute value in user object for user key \"{}\": {}", user.getKey(), value);
          return false;
        }
        if (matchAny(value)) {
          return maybeNegate(true);
        }
      }
      return maybeNegate(false);
    } else if (userValue.getType() != LDValueType.OBJECT) {
      return maybeNegate(matchAny(userValue));
    }
    logger.warn("Got unexpected user attribute type \"{}\" for user key \"{}\" and attribute \"{}\"",
        userValue.getType(), user.getKey(), attribute);
    return false;
  }

  boolean matchesUser(FeatureStore store, LDUser user) {
    // In the case of a segment match operator, we check if the user is in any of the segments,
    // and possibly negate
    if (op == Operator.segmentMatch) {
      for (LDValue j: values) {
        if (j.isString()) {
          Segment segment = store.get(SEGMENTS, j.stringValue());
          if (segment != null) {
            if (segment.matchesUser(user)) {
              return maybeNegate(true);
            }
          }
        }
      }
      return maybeNegate(false);
    }
    
    return matchesUserNoSegments(user);
  }
  
  private boolean matchAny(LDValue userValue) {
    if (op != null) {
      for (LDValue v : values) {
        if (op.apply(userValue, v)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean maybeNegate(boolean b) {
    if (negate)
      return !b;
    else
      return b;
  }
}