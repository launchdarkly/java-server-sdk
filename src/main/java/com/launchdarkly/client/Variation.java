package com.launchdarkly.client;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
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

  @Override
  public String toString() {
    return "Variation{" +
            "value=" + value +
            ", weight=" + weight +
            ", userTarget=" + userTarget +
            ", targets=" + targets +
            '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Variation<?> variation = (Variation<?>) o;

    if (weight != variation.weight) return false;
    if (!value.equals(variation.value)) return false;
    if (!userTarget.equals(variation.userTarget)) return false;
    return targets.equals(variation.targets);

  }

  @Override
  public int hashCode() {
    int result = value.hashCode();
    result = 31 * result + weight;
    result = 31 * result + userTarget.hashCode();
    result = 31 * result + targets.hashCode();
    return result;
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
      this.userTarget = new TargetRule("key", "in", new ArrayList<JsonPrimitive>());
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
    List<JsonPrimitive> values;

    private final Logger logger = LoggerFactory.getLogger(TargetRule.class);

    public TargetRule() {

    }

    TargetRule(String attribute, String operator, List<JsonPrimitive> values) {
      this.attribute = attribute;
      this.operator = operator;
      this.values = new ArrayList<JsonPrimitive>(values);
    }

    TargetRule(String attribute, List<JsonPrimitive> values) {
      this(attribute, "in", values);
    }

    public boolean matchTarget(LDUser user) {
      Object uValue = null;
      if (attribute.equals("key")) {
        if (user.getKey() != null) {
          uValue = new JsonPrimitive(user.getKey());
        }
      }
      else if (attribute.equals("ip") && user.getIp() != null) {
        if (user.getIp() != null) {
          uValue = new JsonPrimitive(user.getIp());
        }
      }
      else if (attribute.equals("country")) {
        if (user.getCountry() != null) {
          uValue = new JsonPrimitive(user.getCountry().getAlpha2());
        }
      }
      else if (attribute.equals("email")) {
        if (user.getEmail() != null) {
          uValue = new JsonPrimitive(user.getEmail());
        }
      }
      else if (attribute.equals("firstName")) {
        if (user.getFirstName() != null ) {
          uValue = new JsonPrimitive(user.getFirstName());
        }
      }
      else if (attribute.equals("lastName")) {
        if (user.getLastName() != null) {
          uValue = new JsonPrimitive(user.getLastName());
        }
      }
      else if (attribute.equals("avatar")) {
        if (user.getAvatar() != null) {
          uValue = new JsonPrimitive(user.getAvatar());
        }
      }
      else if (attribute.equals("name")) {
        if (user.getName() != null) {
          uValue = new JsonPrimitive(user.getName());
        }
      }
      else if (attribute.equals("anonymous")) {
        if (user.getAnonymous() != null) {
          uValue = new JsonPrimitive(user.getAnonymous());
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
              else if (values.contains(elt.getAsJsonPrimitive())) {
                return true;
              }
            }
            return false;
          }
          else if (custom.isJsonPrimitive()) {
            return values.contains(custom.getAsJsonPrimitive());
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
