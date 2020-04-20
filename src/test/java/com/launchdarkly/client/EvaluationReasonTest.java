package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@SuppressWarnings("javadoc")
public class EvaluationReasonTest {
  private static final Gson gson = new Gson();
  
  @Test
  public void offProperties() {
    EvaluationReason reason = EvaluationReason.off();
    assertEquals(EvaluationReason.Kind.OFF, reason.getKind());
    assertEquals(-1, reason.getRuleIndex());
    assertNull(reason.getRuleId());
    assertNull(reason.getPrerequisiteKey());
    assertNull(reason.getErrorKind());
    assertNull(reason.getException());
  }
  
  @Test
  public void fallthroughProperties() {
    EvaluationReason reason = EvaluationReason.fallthrough();
    assertEquals(EvaluationReason.Kind.FALLTHROUGH, reason.getKind());
    assertEquals(-1, reason.getRuleIndex());
    assertNull(reason.getRuleId());
    assertNull(reason.getPrerequisiteKey());
    assertNull(reason.getErrorKind());
    assertNull(reason.getException());
  }
  
  @Test
  public void targetMatchProperties() {
    EvaluationReason reason = EvaluationReason.targetMatch();
    assertEquals(EvaluationReason.Kind.TARGET_MATCH, reason.getKind());
    assertEquals(-1, reason.getRuleIndex());
    assertNull(reason.getRuleId());
    assertNull(reason.getPrerequisiteKey());
    assertNull(reason.getErrorKind());
    assertNull(reason.getException());
  }
  
  @Test
  public void ruleMatchProperties() {
    EvaluationReason reason = EvaluationReason.ruleMatch(2, "id");
    assertEquals(EvaluationReason.Kind.RULE_MATCH, reason.getKind());
    assertEquals(2, reason.getRuleIndex());
    assertEquals("id", reason.getRuleId());
    assertNull(reason.getPrerequisiteKey());
    assertNull(reason.getErrorKind());
    assertNull(reason.getException());
  }
  
  @Test
  public void prerequisiteFailedProperties() {
    EvaluationReason reason = EvaluationReason.prerequisiteFailed("prereq-key");
    assertEquals(EvaluationReason.Kind.PREREQUISITE_FAILED, reason.getKind());
    assertEquals(-1, reason.getRuleIndex());
    assertNull(reason.getRuleId());
    assertEquals("prereq-key", reason.getPrerequisiteKey());
    assertNull(reason.getErrorKind());
    assertNull(reason.getException());
  }
  
  @Test
  public void errorProperties() {
    EvaluationReason reason = EvaluationReason.error(EvaluationReason.ErrorKind.CLIENT_NOT_READY);
    assertEquals(EvaluationReason.Kind.ERROR, reason.getKind());
    assertEquals(-1, reason.getRuleIndex());
    assertNull(reason.getRuleId());
    assertNull(reason.getPrerequisiteKey());
    assertEquals(EvaluationReason.ErrorKind.CLIENT_NOT_READY, reason.getErrorKind());
    assertNull(reason.getException());
  }
  
  @Test
  public void exceptionErrorProperties() {
    Exception ex = new Exception("sorry");
    EvaluationReason reason = EvaluationReason.exception(ex);
    assertEquals(EvaluationReason.Kind.ERROR, reason.getKind());
    assertEquals(-1, reason.getRuleIndex());
    assertNull(reason.getRuleId());
    assertNull(reason.getPrerequisiteKey());
    assertEquals(EvaluationReason.ErrorKind.EXCEPTION, reason.getErrorKind());
    assertEquals(ex, reason.getException());
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void deprecatedSubclassProperties() {
    EvaluationReason ro = EvaluationReason.off();
    assertEquals(EvaluationReason.Off.class, ro.getClass());

    EvaluationReason rf = EvaluationReason.fallthrough();
    assertEquals(EvaluationReason.Fallthrough.class, rf.getClass());

    EvaluationReason rtm = EvaluationReason.targetMatch();
    assertEquals(EvaluationReason.TargetMatch.class, rtm.getClass());

    EvaluationReason rrm = EvaluationReason.ruleMatch(2, "id");
    assertEquals(EvaluationReason.RuleMatch.class, rrm.getClass());
    assertEquals(2, ((EvaluationReason.RuleMatch)rrm).getRuleIndex());
    assertEquals("id", ((EvaluationReason.RuleMatch)rrm).getRuleId());

    EvaluationReason rpf = EvaluationReason.prerequisiteFailed("prereq-key");
    assertEquals(EvaluationReason.PrerequisiteFailed.class, rpf.getClass());
    assertEquals("prereq-key", ((EvaluationReason.PrerequisiteFailed)rpf).getPrerequisiteKey());
    
    EvaluationReason re = EvaluationReason.error(EvaluationReason.ErrorKind.CLIENT_NOT_READY);
    assertEquals(EvaluationReason.Error.class, re.getClass());
    assertEquals(EvaluationReason.ErrorKind.CLIENT_NOT_READY, ((EvaluationReason.Error)re).getErrorKind());
    assertNull(((EvaluationReason.Error)re).getException());
    
    Exception ex = new Exception("sorry");
    EvaluationReason ree = EvaluationReason.exception(ex);
    assertEquals(EvaluationReason.Error.class, ree.getClass());
    assertEquals(EvaluationReason.ErrorKind.EXCEPTION, ((EvaluationReason.Error)ree).getErrorKind());
    assertEquals(ex, ((EvaluationReason.Error)ree).getException());
  }
  
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
      EvaluationReason r0 = EvaluationReason.error(errorKind);
      assertEquals(errorKind, r0.getErrorKind());
      EvaluationReason r1 = EvaluationReason.error(errorKind);
      assertSame(r0, r1);
    }
  }
  
  private void assertJsonEqual(String expectedString, String actualString) {
    JsonElement expected = gson.fromJson(expectedString, JsonElement.class);
    JsonElement actual = gson.fromJson(actualString, JsonElement.class);
    assertEquals(expected, actual);
  }
}
