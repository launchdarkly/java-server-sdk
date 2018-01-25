package com.launchdarkly.client;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.google.gson.JsonPrimitive;

@RunWith(Parameterized.class)
public class OperatorParameterizedTest {
  private static JsonPrimitive dateStr1 = new JsonPrimitive("2017-12-06T00:00:00.000-07:00");
  private static JsonPrimitive dateStr2 = new JsonPrimitive("2017-12-06T00:01:01.000-07:00");
  private static JsonPrimitive dateMs1 = new JsonPrimitive(10000000);
  private static JsonPrimitive dateMs2 = new JsonPrimitive(10000001);
  private static JsonPrimitive invalidDate = new JsonPrimitive("hey what's this?");
  private static JsonPrimitive invalidVer = new JsonPrimitive("xbad%ver");
  
  private final Operator op;
  private final JsonPrimitive aValue;
  private final JsonPrimitive bValue;
  private final boolean shouldBe;
  
  public OperatorParameterizedTest(Operator op, JsonPrimitive aValue, JsonPrimitive bValue, boolean shouldBe) {
    this.op = op;
    this.aValue = aValue;
    this.bValue = bValue;
    this.shouldBe = shouldBe;
  }
  
  @Parameterized.Parameters(name = "{1} {0} {2} should be {3}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
      // numeric comparisons
      { Operator.in, ji(99), ji(99), true },
      { Operator.in, jd(99.0001), jd(99.0001), true },
      { Operator.in, ji(99), jd(99.0001), false },
      { Operator.in, jd(99.0001), ji(99), false },
      { Operator.lessThan, ji(99), jd(99.0001), true },
      { Operator.lessThan, jd(99.0001), ji(99), false },
      { Operator.lessThan, ji(99), ji(99), false },
      { Operator.lessThanOrEqual, ji(99), jd(99.0001), true },
      { Operator.lessThanOrEqual, jd(99.0001), ji(99), false },
      { Operator.lessThanOrEqual, ji(99), ji(99), true },
      { Operator.greaterThan, jd(99.0001), ji(99), true },
      { Operator.greaterThan, ji(99), jd(99.0001), false },
      { Operator.greaterThan, ji(99), ji(99), false },
      { Operator.greaterThanOrEqual, jd(99.0001), ji(99), true },
      { Operator.greaterThanOrEqual, ji(99), jd(99.0001), false },
      { Operator.greaterThanOrEqual, ji(99), ji(99), true },
      
      // string comparisons
      { Operator.in, js("x"), js("x"), true },
      { Operator.in, js("x"), js("xyz"), false },
      { Operator.startsWith, js("xyz"), js("x"), true },
      { Operator.startsWith, js("x"), js("xyz"), false },
      { Operator.endsWith, js("xyz"), js("z"), true },
      { Operator.endsWith, js("z"), js("xyz"), false },
      { Operator.contains, js("xyz"), js("y"), true },
      { Operator.contains, js("y"), js("xyz"), false },
      
      // mixed strings and numbers
      { Operator.in, js("99"), ji(99), false },
      { Operator.in, ji(99), js("99"), false },
      { Operator.contains, js("99"), ji(99), false },
      { Operator.startsWith, js("99"), ji(99), false },
      { Operator.endsWith, js("99"), ji(99), false },
      { Operator.lessThanOrEqual, js("99"), ji(99), false },
      { Operator.lessThanOrEqual, ji(99), js("99"), false },
      { Operator.greaterThanOrEqual, js("99"), ji(99), false },
      { Operator.greaterThanOrEqual, ji(99), js("99"), false },
      
      // regex
      { Operator.matches, js("hello world"), js("hello.*rld"), true },
      { Operator.matches, js("hello world"), js("hello.*orl"), true },
      { Operator.matches, js("hello world"), js("l+"), true },
      { Operator.matches, js("hello world"), js("(world|planet)"), true },
      { Operator.matches, js("hello world"), js("aloha"), false },
      
      // dates
      { Operator.before, dateStr1, dateStr2, true },
      { Operator.before, dateMs1, dateMs2, true },
      { Operator.before, dateStr2, dateStr1, false },
      { Operator.before, dateMs2, dateMs1, false },
      { Operator.before, dateStr1, dateStr1, false },
      { Operator.before, dateMs1, dateMs1, false },
      { Operator.before, dateStr1, invalidDate, false },
      { Operator.after, dateStr1, dateStr2, false },
      { Operator.after, dateMs1, dateMs2, false },
      { Operator.after, dateStr2, dateStr1, true },
      { Operator.after, dateMs2, dateMs1, true },
      { Operator.after, dateStr1, dateStr1, false },
      { Operator.after, dateMs1, dateMs1, false },
      { Operator.after, dateStr1, invalidDate, false },
      
      // semver
      { Operator.semVerEqual, js("2.0.1"), js("2.0.1"), true },
      { Operator.semVerEqual, js("2.0"), js("2.0.0"), true },
      { Operator.semVerEqual, js("2"), js("2.0.0"), true },
      { Operator.semVerEqual, js("2-rc1"), js("2.0.0-rc1"), true },
      { Operator.semVerEqual, js("2+build2"), js("2.0.0+build2"), true },
      { Operator.semVerLessThan, js("2.0.0"), js("2.0.1"), true },
      { Operator.semVerLessThan, js("2.0"), js("2.0.1"), true },
      { Operator.semVerLessThan, js("2.0.1"), js("2.0.0"), false },
      { Operator.semVerLessThan, js("2.0.1"), js("2.0"), false },
      { Operator.semVerLessThan, js("2.0.0-rc"), js("2.0.0"), true },
      { Operator.semVerLessThan, js("2.0.0-rc"), js("2.0.0-rc.beta"), true },
      { Operator.semVerGreaterThan, js("2.0.1"), js("2.0.0"), true },
      { Operator.semVerGreaterThan, js("2.0.1"), js("2.0"), true },
      { Operator.semVerGreaterThan, js("2.0.0"), js("2.0.1"), false },
      { Operator.semVerGreaterThan, js("2.0"), js("2.0.1"), false },
      { Operator.semVerGreaterThan, js("2.0.0-rc.1"), js("2.0.0-rc.0"), true },
      { Operator.semVerLessThan, js("2.0.1"), invalidVer, false },
      { Operator.semVerGreaterThan, js("2.0.1"), invalidVer, false }
    });
  }

  @Test
  public void parameterizedTestComparison() {
    assertEquals(shouldBe, op.apply(aValue, bValue));
  }
  
  private static JsonPrimitive js(String s) {
    return new JsonPrimitive(s);
  }
  
  private static JsonPrimitive ji(int n) {
    return new JsonPrimitive(n);
  }
  
  private static JsonPrimitive jd(double d) {
    return new JsonPrimitive(d);
  }
}
