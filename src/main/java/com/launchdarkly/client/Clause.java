package com.launchdarkly.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;

import java.util.List;

class Clause {
  private final static Logger logger = LoggerFactory.getLogger(Clause.class);

  private String attribute;
  private Operator op;
  private List<JsonPrimitive> values; //interpreted as an OR of values
  private boolean negate;

  public Clause() {
  }
  
  public Clause(String attribute, Operator op, List<JsonPrimitive> values, boolean negate) {
    this.attribute = attribute;
    this.op = op;
    this.values = values;
    this.negate = negate;
  }

  boolean matchesUserNoSegments(LDUser user) {
    JsonElement userValue = user.getValueForEvaluation(attribute);
    if (userValue == null) {
      return false;
    }

    if (userValue.isJsonArray()) {
      JsonArray array = userValue.getAsJsonArray();
      for (JsonElement jsonElement : array) {
        if (!jsonElement.isJsonPrimitive()) {
          logger.error("Invalid custom attribute value in user object: " + jsonElement);
          return false;
        }
        if (matchAny(jsonElement.getAsJsonPrimitive())) {
          return maybeNegate(true);
        }
      }
      return maybeNegate(false);
    } else if (userValue.isJsonPrimitive()) {
      return maybeNegate(matchAny(userValue.getAsJsonPrimitive()));
    }
    logger.warn("Got unexpected user attribute type: " + userValue.getClass().getName() + " for user key: "
        + user.getKey() + " and attribute: " + attribute);
    return false;
  }

  boolean matchesUser(FeatureStore store, LDUser user) {
    // In the case of a segment match operator, we check if the user is in any of the segments,
    // and possibly negate
    if (op == Operator.segmentMatch) {
      for (JsonPrimitive j: values) {
        if (j.isString()) {
          Segment segment = store.get(SEGMENTS, j.getAsString());
          if (segment != null) {
            if (segment.matchUser(user).isMatch()) {
              return maybeNegate(true);
            }
          }
        }
      }
      return maybeNegate(false);
    }
    
    return matchesUserNoSegments(user);
  }
  
  private boolean matchAny(JsonPrimitive userValue) {
    for (JsonPrimitive v : values) {
      if (op.apply(userValue, v)) {
        return true;
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