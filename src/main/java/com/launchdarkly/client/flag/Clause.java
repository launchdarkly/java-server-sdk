package com.launchdarkly.client.flag;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.launchdarkly.client.LDUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Clause {
  private final static Logger logger = LoggerFactory.getLogger(Clause.class);

  private String attribute;
  private Operator op;
  private List<JsonPrimitive> values; //interpreted as an OR of values
  private boolean negate;

  public boolean matchesUser(LDUser user) {
    JsonElement userValue = valueOf(user, attribute);
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
    }
    if (userValue.isJsonPrimitive()) {
      return maybeNegate(matchAny(userValue.getAsJsonPrimitive()));
    }
    logger.error("Got unexpected user attribute type: " + userValue.getClass().getName() + " for user key: "
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

  protected static JsonElement valueOf(LDUser user, String attribute) {
    switch (attribute) {
      case "key":
        return user.getKey();
      case "ip":
        return user.getIp();
      case "country":
        return user.getCountry();
      case "email":
        return user.getEmail();
      case "firstName":
        return user.getFirstName();
      case "lastName":
        return user.getLastName();
      case "avatar":
        return user.getAvatar();
      case "name":
        return user.getName();
      case "anonymous":
        return user.getAnonymous();
    }
    return user.getCustom(attribute);
  }
}
