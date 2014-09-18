package com.launchdarkly.client;


import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class FeatureRep<E> {
  String name;
  String key;
  String salt;
  boolean on;
  List<Variation<E>> variations;

  private static final float long_scale = (float)0xFFFFFFFFFFFFFFFL;

  public FeatureRep() {

  }

  FeatureRep(Builder<E> b) {
    this.name = b.name;
    this.key = b.key;
    this.salt = b.salt;
    this.on = b.on;
    this.variations = new ArrayList<Variation<E>>(b.variations);
  }

  private Float paramForUser(LDUser user) {
    String idHash;
    String hash;

    if (user.getKey() != null) {
      idHash = user.getKey();
    }
    else {
      return null;
    }

    if (user.getSecondary() != null) {
      idHash += "." + user.getSecondary();
    }

    hash = DigestUtils.shaHex(key + "." + salt + "." + idHash).substring(0,15);

    long longVal = Long.parseLong(hash, 16);

    float result =  (float) longVal / long_scale;

    return result;
  }

  public E evaluate(LDUser user) {
    if (!on || user == null) {
      return null;
    }

    Float param = paramForUser(user);

    if (param == null) {
      return null;
    }
    else {
      for (Variation<E> variation : variations) {
        if (variation.matchTarget(user)) {
          return variation.value;
        }
      }

      float sum = 0.0f;
      for (Variation<E> variation : variations) {
        sum += ((float)variation.weight) / 100.0;

        if (param < sum) {
          return variation.value;
        }

      }
    }
      return null;
  }

  static class Builder<E> {
    private String name;
    private String key;
    private boolean on;
    private String salt;
    private List<Variation<E>> variations;

    Builder(String name, String key) {
      this.on = true;
      this.name = name;
      this.key = key;
      this.salt = UUID.randomUUID().toString();
      this.variations = new ArrayList<Variation<E>>();
    }

    Builder<E> salt(String s) {
      this.salt = s;
      return this;
    }

    Builder<E> on(boolean b) {
      this.on = b;
      return this;
    }

    Builder<E> variation(Variation<E> v) {
      variations.add(v);
      return this;
    }

    FeatureRep<E> build() {
      return new FeatureRep<E>(this);
    }

  }
}
