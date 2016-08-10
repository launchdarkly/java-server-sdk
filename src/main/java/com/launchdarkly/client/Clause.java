package com.launchdarkly.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

class Clause {
  private final static Logger logger = LoggerFactory.getLogger(Clause.class);

  private String attribute;
  private Operator op;
  private List<JsonPrimitive> values; //interpreted as an OR of values
  private boolean negate;

  boolean matchesUser(LDUser user) {
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
