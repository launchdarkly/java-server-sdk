package com.launchdarkly.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("deprecation")
class Segment implements VersionedData {
  private String key;
  private Set<String> included;
  private Set<String> excluded;
  private String salt;
  private List<SegmentRule> rules;
  private int version;
  private boolean deleted;

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
    private Set<String> included = new HashSet<>();
    private Set<String> excluded = new HashSet<>();
    private String salt = "";
    private List<SegmentRule> rules = new ArrayList<>();
    private int version = 0;
    private boolean deleted;

    public Builder(String key) {
      this.key = key;
    }
    
    public Builder(Segment from) {
      this.key = from.key;
      this.included = new HashSet<>(from.included);
      this.excluded = new HashSet<>(from.excluded);
      this.salt = from.salt;
      this.rules = new ArrayList<>(from.rules);
      this.version = from.version;
      this.deleted = from.deleted;
    }
    
    public Segment build() {
      return new Segment(this);
    }
    
    public Builder included(Collection<String> included) {
      this.included = new HashSet<>(included);
      return this;
    }
    
    public Builder excluded(Collection<String> excluded) {
      this.excluded = new HashSet<>(excluded);
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