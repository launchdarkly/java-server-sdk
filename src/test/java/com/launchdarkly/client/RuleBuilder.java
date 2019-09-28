package com.launchdarkly.client;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.client.VariationOrRollout.Rollout;

import java.util.ArrayList;
import java.util.List;

public class RuleBuilder {
  private String id;
  private List<Clause> clauses = new ArrayList<>();
  private Integer variation;
  private Rollout rollout;
  private boolean trackEvents;

  public Rule build() {
    return new Rule(id, clauses, variation, rollout, trackEvents);
  }
  
  public RuleBuilder id(String id) {
    this.id = id;
    return this;
  }
  
  public RuleBuilder clauses(Clause... clauses) {
    this.clauses = ImmutableList.copyOf(clauses);
    return this;
  }
  
  public RuleBuilder variation(Integer variation) {
    this.variation = variation;
    return this;
  }
  
  public RuleBuilder rollout(Rollout rollout) {
    this.rollout = rollout;
    return this;
  }
  
  public RuleBuilder trackEvents(boolean trackEvents) {
    this.trackEvents = trackEvents;
    return this;
  }
}
