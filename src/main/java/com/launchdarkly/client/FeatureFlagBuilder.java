package com.launchdarkly.client;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class FeatureFlagBuilder {
  private String key;
  private int version;
  private boolean on;
  private List<Prerequisite> prerequisites = new ArrayList<>();
  private String salt;
  private List<Target> targets = new ArrayList<>();
  private List<Rule> rules = new ArrayList<>();
  private VariationOrRollout fallthrough;
  private Integer offVariation;
  private List<JsonElement> variations = new ArrayList<>();
  private boolean deleted;

  FeatureFlagBuilder(String key) {
    this.key = key;
  }

  FeatureFlagBuilder(FeatureFlag f) {
    if (f != null) {
      this.key = f.getKey();
      this.version = f.getVersion();
      this.on = f.isOn();
      this.prerequisites = f.getPrerequisites();
      this.salt = f.getSalt();
      this.targets = f.getTargets();
      this.rules = f.getRules();
      this.fallthrough = f.getFallthrough();
      this.offVariation = f.getOffVariation();
      this.variations = f.getVariations();
      this.deleted = f.isDeleted();
    }
  }


  FeatureFlagBuilder version(int version) {
    this.version = version;
    return this;
  }

  FeatureFlagBuilder on(boolean on) {
    this.on = on;
    return this;
  }

  FeatureFlagBuilder prerequisites(List<Prerequisite> prerequisites) {
    this.prerequisites = prerequisites;
    return this;
  }

  FeatureFlagBuilder salt(String salt) {
    this.salt = salt;
    return this;
  }

  FeatureFlagBuilder targets(List<Target> targets) {
    this.targets = targets;
    return this;
  }

  FeatureFlagBuilder rules(List<Rule> rules) {
    this.rules = rules;
    return this;
  }

  FeatureFlagBuilder fallthrough(VariationOrRollout fallthrough) {
    this.fallthrough = fallthrough;
    return this;
  }

  FeatureFlagBuilder offVariation(Integer offVariation) {
    this.offVariation = offVariation;
    return this;
  }

  FeatureFlagBuilder variations(List<JsonElement> variations) {
    this.variations = variations;
    return this;
  }

  FeatureFlagBuilder variations(JsonElement... variations) {
    return variations(Arrays.asList(variations));
  }
  
  FeatureFlagBuilder deleted(boolean deleted) {
    this.deleted = deleted;
    return this;
  }

  FeatureFlag build() {
    return new FeatureFlag(key, version, on, prerequisites, salt, targets, rules, fallthrough, offVariation, variations, deleted);
  }
}