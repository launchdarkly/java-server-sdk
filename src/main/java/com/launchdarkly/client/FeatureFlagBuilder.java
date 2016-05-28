package com.launchdarkly.client;

import com.google.gson.JsonElement;

import java.util.List;

public class FeatureFlagBuilder {
  private String key;
  private int version;
  private boolean on;
  private List<Prerequisite> prerequisites;
  private String salt;
  private String sel;
  private List<Target> targets;
  private List<Rule> rules;
  private Rule fallthrough;
  private Integer offVariation;
  private List<JsonElement> variations;
  private boolean deleted;

  public FeatureFlagBuilder(String key) {
    this.key = key;
  }

  public FeatureFlagBuilder version(int version) {
    this.version = version;
    return this;
  }

  public FeatureFlagBuilder on(boolean on) {
    this.on = on;
    return this;
  }

  public FeatureFlagBuilder prerequisites(List<Prerequisite> prerequisites) {
    this.prerequisites = prerequisites;
    return this;
  }

  public FeatureFlagBuilder salt(String salt) {
    this.salt = salt;
    return this;
  }

  public FeatureFlagBuilder sel(String sel) {
    this.sel = sel;
    return this;
  }

  public FeatureFlagBuilder targets(List<Target> targets) {
    this.targets = targets;
    return this;
  }

  public FeatureFlagBuilder rules(List<Rule> rules) {
    this.rules = rules;
    return this;
  }

  public FeatureFlagBuilder fallthrough(Rule fallthrough) {
    this.fallthrough = fallthrough;
    return this;
  }

  public FeatureFlagBuilder offVariation(Integer offVariation) {
    this.offVariation = offVariation;
    return this;
  }

  public FeatureFlagBuilder variations(List<JsonElement> variations) {
    this.variations = variations;
    return this;
  }

  public FeatureFlagBuilder deleted(boolean deleted) {
    this.deleted = deleted;
    return this;
  }

  public FeatureFlag build() {
    return new FeatureFlag(key, version, on, prerequisites, salt, sel, targets, rules, fallthrough, offVariation, variations, deleted);
  }
}