package com.launchdarkly.client;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

class Variation<E> {
  E value;
  int weight;
  List<String> matches;
  List<TargetRule> targets;
  private final Logger logger = LoggerFactory.getLogger(Variation.class);

  public Variation() {

  }

  public boolean matchTarget(LDUser user) {
    for (TargetRule target: targets) {
      if (matchTarget(user)) {
        return true;
      }
    }
    return false;
  }

  class TargetRule {
    String attribute;
    String operator;
    List<String> values;

    public boolean matchTarget(LDUser user) {
      String uValue;
      if (attribute.equals("key") && user.getKey() != null) {
        uValue = user.getKey();
      }
      else if (attribute.equals("ip") && user.getIp() != null) {
        uValue = user.getIp();
      }
      else if (attribute.equals("country") && user.getCountry() != null) {
        uValue = user.getCountry();
      }
      else { // Custom attribute
        JsonElement custom = user.getCustom(attribute);

        if (custom != null) {
          if (custom.isJsonArray()) {
            JsonArray array = custom.getAsJsonArray();
            for (JsonElement elt: array) {
              if (! elt.isJsonPrimitive()) {
                logger.error("Invalid custom attribute value in user object: " + elt);
                return false;
              }
              else if (values.contains(elt.getAsString())) {
                return true;
              }
            }
            return false;
          }
          else if (custom.isJsonPrimitive()) {
            return values.contains(custom.getAsString());
          }
        }
        return false;
      }
      return values.contains((uValue));
    }
  }
}
