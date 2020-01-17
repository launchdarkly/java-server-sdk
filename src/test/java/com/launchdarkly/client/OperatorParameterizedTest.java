package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class OperatorParameterizedTest {
  private static LDValue dateStr1 = LDValue.of("2017-12-06T00:00:00.000-07:00");
  private static LDValue dateStr2 = LDValue.of("2017-12-06T00:01:01.000-07:00");
  private static LDValue dateMs1 = LDValue.of(10000000);
  private static LDValue dateMs2 = LDValue.of(10000001);
  private static LDValue invalidDate = LDValue.of("hey what's this?");
  private static LDValue invalidVer = LDValue.of("xbad%ver");
  
  private final Operator op;
  private final LDValue aValue;
  private final LDValue bValue;
  private final boolean shouldBe;
  
  public OperatorParameterizedTest(Operator op, LDValue aValue, LDValue bValue, boolean shouldBe) {
    this.op = op;
    this.aValue = aValue;
    this.bValue = bValue;
    this.shouldBe = shouldBe;
  }
  
  @Parameterized.Parameters(name = "{1} {0} {2} should be {3}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
      // numeric comparisons
      { Operator.in, LDValue.of(99), LDValue.of(99), true },
      { Operator.in, LDValue.of(99.0001), LDValue.of(99.0001), true },
      { Operator.in, LDValue.of(99), LDValue.of(99.0001), false },
      { Operator.in, LDValue.of(99.0001), LDValue.of(99), false },
      { Operator.lessThan, LDValue.of(99), LDValue.of(99.0001), true },
      { Operator.lessThan, LDValue.of(99.0001), LDValue.of(99), false },
      { Operator.lessThan, LDValue.of(99), LDValue.of(99), false },
      { Operator.lessThanOrEqual, LDValue.of(99), LDValue.of(99.0001), true },
      { Operator.lessThanOrEqual, LDValue.of(99.0001), LDValue.of(99), false },
      { Operator.lessThanOrEqual, LDValue.of(99), LDValue.of(99), true },
      { Operator.greaterThan, LDValue.of(99.0001), LDValue.of(99), true },
      { Operator.greaterThan, LDValue.of(99), LDValue.of(99.0001), false },
      { Operator.greaterThan, LDValue.of(99), LDValue.of(99), false },
      { Operator.greaterThanOrEqual, LDValue.of(99.0001), LDValue.of(99), true },
      { Operator.greaterThanOrEqual, LDValue.of(99), LDValue.of(99.0001), false },
      { Operator.greaterThanOrEqual, LDValue.of(99), LDValue.of(99), true },
      
      // string comparisons
      { Operator.in, LDValue.of("x"), LDValue.of("x"), true },
      { Operator.in, LDValue.of("x"), LDValue.of("xyz"), false },
      { Operator.startsWith, LDValue.of("xyz"), LDValue.of("x"), true },
      { Operator.startsWith, LDValue.of("x"), LDValue.of("xyz"), false },
      { Operator.endsWith, LDValue.of("xyz"), LDValue.of("z"), true },
      { Operator.endsWith, LDValue.of("z"), LDValue.of("xyz"), false },
      { Operator.contains, LDValue.of("xyz"), LDValue.of("y"), true },
      { Operator.contains, LDValue.of("y"), LDValue.of("xyz"), false },
      
      // mixed strings and numbers
      { Operator.in, LDValue.of("99"), LDValue.of(99), false },
      { Operator.in, LDValue.of(99), LDValue.of("99"), false },
      { Operator.contains, LDValue.of("99"), LDValue.of(99), false },
      { Operator.startsWith, LDValue.of("99"), LDValue.of(99), false },
      { Operator.endsWith, LDValue.of("99"), LDValue.of(99), false },
      { Operator.lessThanOrEqual, LDValue.of("99"), LDValue.of(99), false },
      { Operator.lessThanOrEqual, LDValue.of(99), LDValue.of("99"), false },
      { Operator.greaterThanOrEqual, LDValue.of("99"), LDValue.of(99), false },
      { Operator.greaterThanOrEqual, LDValue.of(99), LDValue.of("99"), false },
      
      // regex
      { Operator.matches, LDValue.of("hello world"), LDValue.of("hello.*rld"), true },
      { Operator.matches, LDValue.of("hello world"), LDValue.of("hello.*orl"), true },
      { Operator.matches, LDValue.of("hello world"), LDValue.of("l+"), true },
      { Operator.matches, LDValue.of("hello world"), LDValue.of("(world|planet)"), true },
      { Operator.matches, LDValue.of("hello world"), LDValue.of("aloha"), false },
      
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
      { Operator.semVerEqual, LDValue.of("2.0.1"), LDValue.of("2.0.1"), true },
      { Operator.semVerEqual, LDValue.of("2.0"), LDValue.of("2.0.0"), true },
      { Operator.semVerEqual, LDValue.of("2"), LDValue.of("2.0.0"), true },
      { Operator.semVerEqual, LDValue.of("2-rc1"), LDValue.of("2.0.0-rc1"), true },
      { Operator.semVerEqual, LDValue.of("2+build2"), LDValue.of("2.0.0+build2"), true },
      { Operator.semVerLessThan, LDValue.of("2.0.0"), LDValue.of("2.0.1"), true },
      { Operator.semVerLessThan, LDValue.of("2.0"), LDValue.of("2.0.1"), true },
      { Operator.semVerLessThan, LDValue.of("2.0.1"), LDValue.of("2.0.0"), false },
      { Operator.semVerLessThan, LDValue.of("2.0.1"), LDValue.of("2.0"), false },
      { Operator.semVerLessThan, LDValue.of("2.0.0-rc"), LDValue.of("2.0.0"), true },
      { Operator.semVerLessThan, LDValue.of("2.0.0-rc"), LDValue.of("2.0.0-rc.beta"), true },
      { Operator.semVerGreaterThan, LDValue.of("2.0.1"), LDValue.of("2.0.0"), true },
      { Operator.semVerGreaterThan, LDValue.of("2.0.1"), LDValue.of("2.0"), true },
      { Operator.semVerGreaterThan, LDValue.of("2.0.0"), LDValue.of("2.0.1"), false },
      { Operator.semVerGreaterThan, LDValue.of("2.0"), LDValue.of("2.0.1"), false },
      { Operator.semVerGreaterThan, LDValue.of("2.0.0-rc.1"), LDValue.of("2.0.0-rc.0"), true },
      { Operator.semVerLessThan, LDValue.of("2.0.1"), invalidVer, false },
      { Operator.semVerGreaterThan, LDValue.of("2.0.1"), invalidVer, false },
      { Operator.semVerEqual, LDValue.ofNull(), LDValue.of("2.0.0"), false },
      { Operator.semVerEqual, LDValue.of(1), LDValue.of("2.0.0"), false },
      { Operator.semVerEqual, LDValue.of(true), LDValue.of("2.0.0"), false },
      { Operator.semVerEqual, LDValue.of("2.0.0"), LDValue.ofNull(), false },
      { Operator.semVerEqual, LDValue.of("2.0.0"), LDValue.of(1), false },
      { Operator.semVerEqual, LDValue.of("2.0.0"), LDValue.of(true), false }
    });
  }

  @Test
  public void parameterizedTestComparison() {
    assertEquals(shouldBe, op.apply(aValue, bValue));
  }
}
