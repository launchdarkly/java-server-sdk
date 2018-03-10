package com.launchdarkly.client;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.gson.reflect.TypeToken;

class Segment implements VersionedData {

  private static final Type mapType = new TypeToken<Map<String, Segment>>() { }.getType();

  private String key;
  private List<String> included;
  private List<String> excluded;
  private String salt;
  private List<SegmentRule> rules;
  private int version;
  private boolean deleted;

  static Segment fromJson(LDConfig config, String json) {
    return config.gson.fromJson(json, Segment.class);
  }

  static Map<String, Segment> fromJsonMap(LDConfig config, String json) {
    return config.gson.fromJson(json, mapType);
  }

  // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
  Segment() {}

  private Segment(Builder builder) {
    this.key = builder.key;
    this.included = builder.included;
    this.excluded = builder.excluded;
    this.salt = builder.salt;
    this.rules = builder.rules;
    this.version = builder.version;
    this.deleted = builder.deleted;
  }

  public String getKey() {
    return key;
  }
  
  public Iterable<String> getIncluded() {
    return included;
  }
  
  public Iterable<String> getExcluded() {
    return excluded;
  }
  
  public String getSalt() {
    return salt;
  }
  
  public Iterable<SegmentRule> getRules() {
    return rules;
  }
  
  public int getVersion() {
    return version;
  }
  
  public boolean isDeleted() {
    return deleted;
  }
  
  public boolean matchesUser(LDUser user) {
    String key = user.getKeyAsString();
    if (key == null) {
      return false;
    }
    if (included.contains(key)) {
      return true;
    }
    if (excluded.contains(key)) {
      return false;
    }
    for (SegmentRule rule: rules) {
      if (rule.matchUser(user, key, salt)) {
        return true;
      }
    }
    return false;
  }
  
  public static class Builder {
    private String key;
    private List<String> included = new ArrayList<>();
    private List<String> excluded = new ArrayList<>();
    private String salt = "";
    private List<SegmentRule> rules = new ArrayList<>();
    private int version = 0;
    private boolean deleted;

    public Builder(String key) {
      this.key = key;
    }
    
    public Builder(Segment from) {
      this.key = from.key;
      this.included = new ArrayList<>(from.included);
      this.excluded = new ArrayList<>(from.excluded);
      this.salt = from.salt;
      this.rules = new ArrayList<>(from.rules);
      this.version = from.version;
      this.deleted = from.deleted;
    }
    
    public Segment build() {
      return new Segment(this);
    }
    
    public Builder included(Collection<String> included) {
      this.included = new ArrayList<>(included);
      return this;
    }
    
    public Builder excluded(Collection<String> excluded) {
      this.excluded = new ArrayList<>(excluded);
      return this;
    }
    
    public Builder salt(String salt) {
      this.salt = salt;
      return this;
    }
    
    public Builder rules(Collection<SegmentRule> rules) {
      this.rules = new ArrayList<>(rules);
      return this;
    }
    
    public Builder version(int version) {
      this.version = version;
      return this;
    }
    
    public Builder deleted(boolean deleted) {
      this.deleted = deleted;
      return this;
    }
  }
}