package com.launchdarkly.client;

import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings("javadoc")
public class FlagModelDeserializationTest {
  private static final Gson gson = new Gson();
  
  @Test
  public void precomputedReasonsAreAddedToPrerequisites() {
    String flagJson = "{\"key\":\"flagkey\",\"prerequisites\":[{\"key\":\"prereq0\"},{\"key\":\"prereq1\"}]}";
    DataModel.FeatureFlag flag = gson.fromJson(flagJson, DataModel.FeatureFlag.class);
    assertNotNull(flag.getPrerequisites());
    assertEquals(2, flag.getPrerequisites().size());
    assertEquals(EvaluationReason.prerequisiteFailed("prereq0"), flag.getPrerequisites().get(0).getPrerequisiteFailedReason());
    assertEquals(EvaluationReason.prerequisiteFailed("prereq1"), flag.getPrerequisites().get(1).getPrerequisiteFailedReason());
  }
  
  @Test
  public void precomputedReasonsAreAddedToRules() {
    String flagJson = "{\"key\":\"flagkey\",\"rules\":[{\"id\":\"ruleid0\"},{\"id\":\"ruleid1\"}]}";
    DataModel.FeatureFlag flag = gson.fromJson(flagJson, DataModel.FeatureFlag.class);
    assertNotNull(flag.getRules());
    assertEquals(2, flag.getRules().size());
    assertEquals(EvaluationReason.ruleMatch(0, "ruleid0"), flag.getRules().get(0).getRuleMatchReason());
    assertEquals(EvaluationReason.ruleMatch(1, "ruleid1"), flag.getRules().get(1).getRuleMatchReason());
  }
}
