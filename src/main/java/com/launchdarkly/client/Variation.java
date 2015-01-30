package com.launchdarkly.client;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

class Variation<E> {
  E value;
  int weight;
  TargetRule userTarget;
  List<TargetRule> targets;
  private final Logger logger = LoggerFactory.getLogger(Variation.class);

  public Variation() {

  }

  Variation(Builder<E> b) {
    this.value = b.value;
    this.weight = b.weight;
    this.userTarget = b.userTarget;
    this.targets = new ArrayList<TargetRule>(b.targets);
  }

  public boolean matchUser(LDUser user) {
    // If a userTarget rule is present, apply it
    if (userTarget != null && userTarget.matchTarget(user)) {
      return true;
    }
    return false;
  }

  public boolean matchTarget(LDUser user) {
    for (TargetRule target: targets) {
      // If a userTarget rule is present, nested "key" rules
      // are deprecated and should be ignored
      if (userTarget != null && target.attribute == "key") {
        continue;
      }
      if (target.matchTarget(user)) {
        return true;
      }
    }
    return false;
  }

  static class Builder<E> {
    E value;
    int weight;
    TargetRule userTarget;
    List<TargetRule> targets;

    Builder(E value, int weight) {
      this.value = value;
      this.weight = weight;
      this.userTarget = new TargetRule("key", "in", new ArrayList<String>());
      targets = new ArrayList<TargetRule>();
    }

    Builder<E> userTarget(TargetRule rule) {
      this.userTarget = rule;
      return this;
    }

    Builder<E> target(TargetRule rule) {
      targets.add(rule);
      return this;
    }

    Variation<E> build() {
      return new Variation<E>(this);
    }

  }

  static class TargetRule {
    String attribute;
    String operator;
    List<String> values;

    private final Logger logger = LoggerFactory.getLogger(TargetRule.class);

    TargetRule(String attribute, String operator, List<String> values) {
      this.attribute = attribute;
      this.operator = operator;
      this.values = new ArrayList<String>(values);
    }

    TargetRule(String attribute, List<String> values) {
      this(attribute, "in", values);
    }

    public boolean matchTarget(LDUser user) {
      String uValue = null;
      if (attribute.equals("key")) {
        if (user.getKey() != null) {
          uValue = user.getKey();
        }
      }
      else if (attribute.equals("ip") && user.getIp() != null) {
        if (user.getIp() != null) {
          uValue = user.getIp();
        }
      }
      else if (attribute.equals("country")) {
        if (user.getCountry() != null) {
          uValue = user.getCountry().getAlpha2();
        }
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
      if (uValue == null) {
        return false;
      }
      return values.contains((uValue));
    }
  }
}
