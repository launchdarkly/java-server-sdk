package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OperatorTest {
  @Test
  public void testNumberComparison() {
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
