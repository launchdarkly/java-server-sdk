package com.launchdarkly.client;

import com.google.gson.Gson;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@SuppressWarnings("javadoc")
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
  public void testFallthroughSerialization() {
    EvaluationReason reason = EvaluationReason.fallthrough();
    String json = "{\"kind\":\"FALLTHROUGH\"}";
    assertJsonEqual(json, gson.toJson(reason));
    assertEquals("FALLTHROUGH", reason.toString());
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
  public void testPrerequisiteFailedSerialization() {
    EvaluationReason reason = EvaluationReason.prerequisiteFailed("key");
    String json = "{\"kind\":\"PREREQUISITE_FAILED\",\"prerequisiteKey\":\"key\"}";
    assertJsonEqual(json, gson.toJson(reason));
    assertEquals("PREREQUISITE_FAILED(key)", reason.toString());
  }
  
  @Test
  public void testErrorSerialization() {
    EvaluationReason reason = EvaluationReason.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND);
    String json = "{\"kind\":\"ERROR\",\"errorKind\":\"FLAG_NOT_FOUND\"}";
    assertJsonEqual(json, gson.toJson(reason));
    assertEquals("ERROR(FLAG_NOT_FOUND)", reason.toString());
  }

  @Test
  public void testErrorSerializationWithException() {
    // We do *not* want the JSON representation to include the exception, because that is used in events, and
    // the LD event service won't know what to do with that field (which will also contain a big stacktrace).
    EvaluationReason reason = EvaluationReason.exception(new Exception("something happened"));
    String json = "{\"kind\":\"ERROR\",\"errorKind\":\"EXCEPTION\"}";
    assertJsonEqual(json, gson.toJson(reason));
    assertEquals("ERROR(EXCEPTION,java.lang.Exception: something happened)", reason.toString());
  }
  
  @Test
  public void errorInstancesAreReused() {
    for (EvaluationReason.ErrorKind errorKind: EvaluationReason.ErrorKind.values()) {
      EvaluationReason.Error r0 = EvaluationReason.error(errorKind);
      assertEquals(errorKind, r0.getErrorKind());
      EvaluationReason.Error r1 = EvaluationReason.error(errorKind);
      assertSame(r0, r1);
    }
  }
  
  private void assertJsonEqual(String expectedString, String actualString) {
    LDValue expected = LDValue.parse(expectedString);
    LDValue actual = LDValue.parse(actualString);
    assertEquals(expected, actual);
  }
}
