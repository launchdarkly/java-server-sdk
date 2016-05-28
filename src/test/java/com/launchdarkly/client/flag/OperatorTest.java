package com.launchdarkly.client.flag;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OperatorTest {
  @Test
  public void testNumberComparison() {
    Gson gson = new Gson();

    JsonPrimitive a = new JsonPrimitive(99);
    JsonPrimitive b = new JsonPrimitive(99.0001);

    assertFalse(Operator.contains.apply(a, b));
    assertTrue(Operator.lessThan.apply(a, b));
    assertTrue(Operator.lessThanOrEqual.apply(a, b));
    assertFalse(Operator.greaterThan.apply(a, b));
    assertFalse(Operator.greaterThanOrEqual.apply(a, b));

    assertFalse(Operator.contains.apply(b, a));
    assertFalse(Operator.lessThan.apply(b, a));
    assertFalse(Operator.lessThanOrEqual.apply(b, a));
    assertTrue(Operator.greaterThan.apply(b, a));
    assertTrue(Operator.greaterThanOrEqual.apply(b, a));
  }
}
