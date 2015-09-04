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
  boolean deleted;
  int version;

  private static final float long_scale = (float)0xFFFFFFFFFFFFFFFL;

  public FeatureRep() {

  }

  @Override
  public String toString() {
    return "FeatureRep{" +
            "name='" + name + '\'' +
            ", key='" + key + '\'' +
            ", salt='" + salt + '\'' +
            ", on=" + on +
            ", variations=" + variations +
            ", deleted=" + deleted +
            ", version=" + version +
            '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FeatureRep<?> that = (FeatureRep<?>) o;

    if (on != that.on) return false;
    if (deleted != that.deleted) return false;
    if (version != that.version) return false;
    if (!name.equals(that.name)) return false;
    if (!key.equals(that.key)) return false;
    if (!salt.equals(that.salt)) return false;
    return variations.equals(that.variations);

  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + key.hashCode();
    result = 31 * result + salt.hashCode();
    result = 31 * result + (on ? 1 : 0);
    result = 31 * result + variations.hashCode();
    result = 31 * result + (deleted ? 1 : 0);
    result = 31 * result + version;
    return result;
  }

  FeatureRep(Builder<E> b) {
    this.name = b.name;
    this.key = b.key;
    this.salt = b.salt;
    this.on = b.on;
    this.deleted = b.deleted;
    this.version = b.version;
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
      for (Variation<E> variation: variations) {
        if (variation.matchUser(user)) {
          return variation.value;
        }
      }

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
    private boolean deleted;
    private int version;
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

    Builder<E> deleted(boolean d) {
      this.deleted = d;
      return this;
    }

    Builder<E> version(int v) {
      this.version = v;
      return this;
    }

    FeatureRep<E> build() {
      return new FeatureRep<E>(this);
    }

  }
}
