package com.launchdarkly.client;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EvaluationReasonTest {
  private static final Gson gson = new Gson();
  
  @Test
  public void testOffReasonSerialization() {
    EvaluationReason reason = EvaluationReason.off();
    String json = "{\"kind\":\"OFF\"}";
    assertJsonEqual(json, gson.toJson(reason));
    assertEquals("OFF", reason.toString());
  }
  
  @Test
  public void testTargetMatchSerialization() {
    EvaluationReason reason = EvaluationReason.targetMatch();
    String json = "{\"kind\":\"TARGET_MATCH\"}";
    assertJsonEqual(json, gson.toJson(reason));
    assertEquals("TARGET_MATCH", reason.toString());
  }
  
  @Test
  public void testRuleMatchSerialization() {
    EvaluationReason reason = EvaluationReason.ruleMatch(1, "id");
    String json = "{\"kind\":\"RULE_MATCH\",\"ruleIndex\":1,\"ruleId\":\"id\"}";
    assertJsonEqual(json, gson.toJson(reason));
    assertEquals("RULE_MATCH(1,id)", reason.toString());
  }
  
  @Test
  public void testPrerequisitesFailedSerialization() {
    EvaluationReason reason = EvaluationReason.prerequisitesFailed(ImmutableList.of("key1", "key2"));
    String json = "{\"kind\":\"PREREQUISITES_FAILED\",\"prerequisiteKeys\":[\"key1\",\"key2\"]}";
    assertJsonEqual(json, gson.toJson(reason));
    assertEquals("PREREQUISITES_FAILED(key1,key2)", reason.toString());
  }
  
  @Test
  public void testFallthrougSerialization() {
    EvaluationReason reason = EvaluationReason.fallthrough();
    String json = "{\"kind\":\"FALLTHROUGH\"}";
    assertJsonEqual(json, gson.toJson(reason));
    assertEquals("FALLTHROUGH", reason.toString());
  }
  
  @Test
  public void testErrorSerialization() {
    EvaluationReason reason = EvaluationReason.error(EvaluationReason.ErrorKind.EXCEPTION);
    String json = "{\"kind\":\"ERROR\",\"errorKind\":\"EXCEPTION\"}";
    assertJsonEqual(json, gson.toJson(reason));
    assertEquals("ERROR(EXCEPTION)", reason.toString());
  }
  
  private void assertJsonEqual(String expectedString, String actualString) {
    JsonElement expected = gson.fromJson(expectedString, JsonElement.class);
    JsonElement actual = gson.fromJson(actualString, JsonElement.class);
    assertEquals(expected, actual);
  }
}
