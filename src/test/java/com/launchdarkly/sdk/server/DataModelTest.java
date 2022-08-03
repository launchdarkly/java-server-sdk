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
    assertEquals(ImmutableList.of(), flagWithAllZeroValuedFields().getPrerequisites());
  }

  @Test
  public void flagTargetsListCanNeverBeNull() {
    assertEquals(ImmutableList.of(), flagWithAllZeroValuedFields().getTargets());
  }

  @Test
  public void flagContextTargetsListCanNeverBeNull() {
    assertEquals(ImmutableList.of(), flagWithAllZeroValuedFields().getContextTargets());
  }
  
  @Test
  public void flagRulesListCanNeverBeNull() {
    assertEquals(ImmutableList.of(), flagWithAllZeroValuedFields().getRules());
  }

  @Test
  public void flagVariationsListCanNeverBeNull() {
    assertEquals(ImmutableList.of(), flagWithAllZeroValuedFields().getVariations());
  }
  
  @Test
  public void targetKeysSetCanNeverBeNull() {
    Target t = new Target(null, null, 0);
    assertEquals(ImmutableSet.of(), t.getValues());
  }
  
  @Test
  public void ruleClausesListCanNeverBeNull() {
    Rule r = new Rule("id", null, null, null, false);
    assertEquals(ImmutableList.of(), r.getClauses());
  }
  
  @Test
  public void clauseValuesListCanNeverBeNull() {
    Clause c = new Clause(null, null, null, null, false);
    assertEquals(ImmutableList.of(), c.getValues());
  }

  @Test
  public void segmentIncludedCanNeverBeNull() {
    assertEquals(ImmutableSet.of(), segmentWithAllZeroValuedFields().getIncluded());
  }

  @Test
  public void segmentExcludedCanNeverBeNull() {
    assertEquals(ImmutableSet.of(), segmentWithAllZeroValuedFields().getExcluded());
  }

  @Test
  public void segmentIncludedContextsCanNeverBeNull() {
    assertEquals(ImmutableList.of(), segmentWithAllZeroValuedFields().getIncludedContexts());
  }

  @Test
  public void segmentExcludedContextsCanNeverBeNull() {
    assertEquals(ImmutableList.of(), segmentWithAllZeroValuedFields().getExcludedContexts());
  }

  @Test
  public void segmentRulesListCanNeverBeNull() {
    assertEquals(ImmutableList.of(), segmentWithAllZeroValuedFields().getRules());
  }

  @Test
  public void segmentRuleClausesListCanNeverBeNull() {
    SegmentRule r = new SegmentRule(null, null, null, null);
    assertEquals(ImmutableList.of(), r.getClauses());
  }
  
  @Test
  public void rolloutVariationsListCanNeverBeNull() {
    Rollout r = new Rollout(null, null, null, RolloutKind.rollout, null);
    assertEquals(ImmutableList.of(), r.getVariations());
  }
  
  private FeatureFlag flagWithAllZeroValuedFields() {
    // This calls the empty constructor directly to simulate a condition where Gson did not set any fields
    // and no preprocessing has happened.
    return new FeatureFlag();
  }
  
  private Segment segmentWithAllZeroValuedFields() {
    // This calls the empty constructor directly to simulate a condition where Gson did not set any fields
    // and no preprocessing has happened.
    return new Segment();
  }
}
