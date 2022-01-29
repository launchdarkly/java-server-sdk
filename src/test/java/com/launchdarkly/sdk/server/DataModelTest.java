package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Rollout;
import com.launchdarkly.sdk.server.DataModel.RolloutKind;
import com.launchdarkly.sdk.server.DataModel.Rule;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.SegmentRule;
import com.launchdarkly.sdk.server.DataModel.Target;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class DataModelTest {
  @Test
  public void flagPrerequisitesListCanNeverBeNull() {
    FeatureFlag f = new FeatureFlag("key", 0, false, null, null, null, null, null, null, null, false, false, false, null, false);
    assertEquals(ImmutableList.of(), f.getPrerequisites());
  }

  @Test
  public void flagTargetsListCanNeverBeNull() {
    FeatureFlag f = new FeatureFlag("key", 0, false, null, null, null, null, null, null, null, false, false, false, null, false);
    assertEquals(ImmutableList.of(), f.getTargets());
  }
  
  @Test
  public void flagRulesListCanNeverBeNull() {
    FeatureFlag f = new FeatureFlag("key", 0, false, null, null, null, null, null, null, null, false, false, false, null, false);
    assertEquals(ImmutableList.of(), f.getRules());
  }

  @Test
  public void flagVariationsListCanNeverBeNull() {
    FeatureFlag f = new FeatureFlag("key", 0, false, null, null, null, null, null, null, null, false, false, false, null, false);
    assertEquals(ImmutableList.of(), f.getVariations());
  }
  
  @Test
  public void targetKeysSetCanNeverBeNull() {
    Target t = new Target(null, 0);
    assertEquals(ImmutableSet.of(), t.getValues());
  }
  
  @Test
  public void ruleClausesListCanNeverBeNull() {
    Rule r = new Rule("id", null, null, null, false);
    assertEquals(ImmutableList.of(), r.getClauses());
  }
  
  @Test
  public void clauseValuesListCanNeverBeNull() {
    Clause c = new Clause(null, null, null, false);
    assertEquals(ImmutableList.of(), c.getValues());
  }

  @Test
  public void segmentIncludedCanNeverBeNull() {
    Segment s = new Segment("key", null, null, null, null, 0, false, false, null);
    assertEquals(ImmutableSet.of(), s.getIncluded());
  }

  @Test
  public void segmentExcludedCanNeverBeNull() {
    Segment s = new Segment("key", null, null, null, null, 0, false, false, null);
    assertEquals(ImmutableSet.of(), s.getExcluded());
  }

  @Test
  public void segmentRulesListCanNeverBeNull() {
    Segment s = new Segment("key", null, null, null, null, 0, false, false, null);
    assertEquals(ImmutableList.of(), s.getRules());
  }

  @Test
  public void segmentRuleClausesListCanNeverBeNull() {
    SegmentRule r = new SegmentRule(null, null, null);
    assertEquals(ImmutableList.of(), r.getClauses());
  }
  
  @Test
  public void rolloutVariationsListCanNeverBeNull() {
    Rollout r = new Rollout(null, null, RolloutKind.rollout);
    assertEquals(ImmutableList.of(), r.getVariations());
  }
}
