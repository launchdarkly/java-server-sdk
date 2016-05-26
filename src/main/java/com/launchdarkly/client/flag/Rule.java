package com.launchdarkly.client.flag;

import com.google.gson.JsonElement;
import com.launchdarkly.client.LDUser;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.launchdarkly.client.flag.Clause.valueOf;

// Expresses a set of AND-ed matching conditions for a user, along with
// either the fixed variation or percent rollout to serve if the conditions
// match.
// Invariant: one of the variation or rollout must be non-nil.
public class Rule<E> {
  private final static Logger logger = LoggerFactory.getLogger(Rule.class);
  private static final float long_scale = (float) 0xFFFFFFFFFFFFFFFL;

  private List<Clause> clauses;
  private Integer variation;
  private Rollout rollout;

  public boolean matchesUser(LDUser user) {
    for (Clause clause : clauses) {
      if (!clause.matchesUser(user)) {
        return false;
      }
    }
    return true;
  }

  public Integer variationIndexForUser(LDUser user, String key, String salt) {
    if (variation != null) {
      return variation;
    } else if (rollout != null) {
      String bucketBy = rollout.bucketBy == null ? "key" : rollout.bucketBy;
      Float bucket = bucketUser(user, key, bucketBy, salt);
      Float sum = 0F;
      for (WeightedVariation wv : rollout.variations) {
        sum += (float)wv.weight / 100000F;
        if (bucket < sum) {
          return wv.variation;
        }
      }
    }
    return null;
  }

  protected Float bucketUser(LDUser user, String key, String attr, String salt) {
    JsonElement userValue = valueOf(user, attr);
    String idHash;
    if (userValue != null) {
      if (userValue.isJsonPrimitive() && userValue.getAsJsonPrimitive().isString()) {
        idHash = userValue.getAsString();
        if (user.getSecondary() != null) {
          idHash = idHash + "." + user.getSecondary().getAsString();
        }
        String hash = DigestUtils.sha1Hex(key + "." + salt + "." + idHash).substring(0, 15);
        long longVal = Long.parseLong(hash, 16);
        return (float) longVal / long_scale;
      }
    }
    return null;
  }

  private class Rollout {
    private List<WeightedVariation> variations;
    private String bucketBy;
  }

  private class WeightedVariation {
    private int variation;
    private int weight;
  }
}
