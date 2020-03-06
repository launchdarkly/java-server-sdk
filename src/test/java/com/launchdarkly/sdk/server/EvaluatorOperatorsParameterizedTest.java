package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel;
import com.launchdarkly.sdk.server.EvaluatorOperators;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class EvaluatorOperatorsParameterizedTest {
  private static LDValue dateStr1 = LDValue.of("2017-12-06T00:00:00.000-07:00");
  private static LDValue dateStr2 = LDValue.of("2017-12-06T00:01:01.000-07:00");
  private static LDValue dateStrUtc1 = LDValue.of("2017-12-06T00:00:00.000Z");
  private static LDValue dateStrUtc2 = LDValue.of("2017-12-06T00:01:01.000Z");
  private static LDValue dateMs1 = LDValue.of(10000000);
  private static LDValue dateMs2 = LDValue.of(10000001);
  private static LDValue invalidDate = LDValue.of("hey what's this?");
  private static LDValue invalidVer = LDValue.of("xbad%ver");
  
  private final DataModel.Operator op;
  private final LDValue aValue;
  private final LDValue bValue;
  private final boolean shouldBe;
  
  public EvaluatorOperatorsParameterizedTest(DataModel.Operator op, LDValue aValue, LDValue bValue, boolean shouldBe) {
    this.op = op;
    this.aValue = aValue;
    this.bValue = bValue;
    this.shouldBe = shouldBe;
  }
  
  @Parameterized.Parameters(name = "{1} {0} {2} should be {3}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
      // numeric comparisons
      { DataModel.Operator.in, LDValue.of(99), LDValue.of(99), true },
      { DataModel.Operator.in, LDValue.of(99.0001), LDValue.of(99.0001), true },
      { DataModel.Operator.in, LDValue.of(99), LDValue.of(99.0001), false },
      { DataModel.Operator.in, LDValue.of(99.0001), LDValue.of(99), false },
      { DataModel.Operator.lessThan, LDValue.of(99), LDValue.of(99.0001), true },
      { DataModel.Operator.lessThan, LDValue.of(99.0001), LDValue.of(99), false },
      { DataModel.Operator.lessThan, LDValue.of(99), LDValue.of(99), false },
      { DataModel.Operator.lessThanOrEqual, LDValue.of(99), LDValue.of(99.0001), true },
      { DataModel.Operator.lessThanOrEqual, LDValue.of(99.0001), LDValue.of(99), false },
      { DataModel.Operator.lessThanOrEqual, LDValue.of(99), LDValue.of(99), true },
      { DataModel.Operator.greaterThan, LDValue.of(99.0001), LDValue.of(99), true },
      { DataModel.Operator.greaterThan, LDValue.of(99), LDValue.of(99.0001), false },
      { DataModel.Operator.greaterThan, LDValue.of(99), LDValue.of(99), false },
      { DataModel.Operator.greaterThanOrEqual, LDValue.of(99.0001), LDValue.of(99), true },
      { DataModel.Operator.greaterThanOrEqual, LDValue.of(99), LDValue.of(99.0001), false },
      { DataModel.Operator.greaterThanOrEqual, LDValue.of(99), LDValue.of(99), true },
      
      // string comparisons
      { DataModel.Operator.in, LDValue.of("x"), LDValue.of("x"), true },
      { DataModel.Operator.in, LDValue.of("x"), LDValue.of("xyz"), false },
      { DataModel.Operator.startsWith, LDValue.of("xyz"), LDValue.of("x"), true },
      { DataModel.Operator.startsWith, LDValue.of("x"), LDValue.of("xyz"), false },
      { DataModel.Operator.endsWith, LDValue.of("xyz"), LDValue.of("z"), true },
      { DataModel.Operator.endsWith, LDValue.of("z"), LDValue.of("xyz"), false },
      { DataModel.Operator.contains, LDValue.of("xyz"), LDValue.of("y"), true },
      { DataModel.Operator.contains, LDValue.of("y"), LDValue.of("xyz"), false },
      
      // mixed strings and numbers
      { DataModel.Operator.in, LDValue.of("99"), LDValue.of(99), false },
      { DataModel.Operator.in, LDValue.of(99), LDValue.of("99"), false },
      { DataModel.Operator.contains, LDValue.of("99"), LDValue.of(99), false },
      { DataModel.Operator.startsWith, LDValue.of("99"), LDValue.of(99), false },
      { DataModel.Operator.endsWith, LDValue.of("99"), LDValue.of(99), false },
      { DataModel.Operator.lessThanOrEqual, LDValue.of("99"), LDValue.of(99), false },
      { DataModel.Operator.lessThanOrEqual, LDValue.of(99), LDValue.of("99"), false },
      { DataModel.Operator.greaterThanOrEqual, LDValue.of("99"), LDValue.of(99), false },
      { DataModel.Operator.greaterThanOrEqual, LDValue.of(99), LDValue.of("99"), false },
      
      // regex
      { DataModel.Operator.matches, LDValue.of("hello world"), LDValue.of("hello.*rld"), true },
      { DataModel.Operator.matches, LDValue.of("hello world"), LDValue.of("hello.*orl"), true },
      { DataModel.Operator.matches, LDValue.of("hello world"), LDValue.of("l+"), true },
      { DataModel.Operator.matches, LDValue.of("hello world"), LDValue.of("(world|planet)"), true },
      { DataModel.Operator.matches, LDValue.of("hello world"), LDValue.of("aloha"), false },
      
      // dates
      { DataModel.Operator.before, dateStr1, dateStr2, true },
      { DataModel.Operator.before, dateStrUtc1, dateStrUtc2, true },
      { DataModel.Operator.before, dateMs1, dateMs2, true },
      { DataModel.Operator.before, dateStr2, dateStr1, false },
      { DataModel.Operator.before, dateStrUtc2, dateStrUtc1, false },
      { DataModel.Operator.before, dateMs2, dateMs1, false },
      { DataModel.Operator.before, dateStr1, dateStr1, false },
      { DataModel.Operator.before, dateMs1, dateMs1, false },
      { DataModel.Operator.before, dateStr1, invalidDate, false },
      { DataModel.Operator.after, dateStr1, dateStr2, false },
      { DataModel.Operator.after, dateStrUtc1, dateStrUtc2, false },
      { DataModel.Operator.after, dateMs1, dateMs2, false },
      { DataModel.Operator.after, dateStr2, dateStr1, true },
      { DataModel.Operator.after, dateStrUtc2, dateStrUtc1, true },
      { DataModel.Operator.after, dateMs2, dateMs1, true },
      { DataModel.Operator.after, dateStr1, dateStr1, false },
      { DataModel.Operator.after, dateMs1, dateMs1, false },
      { DataModel.Operator.after, dateStr1, invalidDate, false },
      
      // semver
      { DataModel.Operator.semVerEqual, LDValue.of("2.0.1"), LDValue.of("2.0.1"), true },
      { DataModel.Operator.semVerEqual, LDValue.of("2.0"), LDValue.of("2.0.0"), true },
      { DataModel.Operator.semVerEqual, LDValue.of("2"), LDValue.of("2.0.0"), true },
      { DataModel.Operator.semVerEqual, LDValue.of("2-rc1"), LDValue.of("2.0.0-rc1"), true },
      { DataModel.Operator.semVerEqual, LDValue.of("2+build2"), LDValue.of("2.0.0+build2"), true },
      { DataModel.Operator.semVerLessThan, LDValue.of("2.0.0"), LDValue.of("2.0.1"), true },
      { DataModel.Operator.semVerLessThan, LDValue.of("2.0"), LDValue.of("2.0.1"), true },
      { DataModel.Operator.semVerLessThan, LDValue.of("2.0.1"), LDValue.of("2.0.0"), false },
      { DataModel.Operator.semVerLessThan, LDValue.of("2.0.1"), LDValue.of("2.0"), false },
      { DataModel.Operator.semVerLessThan, LDValue.of("2.0.0-rc"), LDValue.of("2.0.0"), true },
      { DataModel.Operator.semVerLessThan, LDValue.of("2.0.0-rc"), LDValue.of("2.0.0-rc.beta"), true },
      { DataModel.Operator.semVerGreaterThan, LDValue.of("2.0.1"), LDValue.of("2.0.0"), true },
      { DataModel.Operator.semVerGreaterThan, LDValue.of("2.0.1"), LDValue.of("2.0"), true },
      { DataModel.Operator.semVerGreaterThan, LDValue.of("2.0.0"), LDValue.of("2.0.1"), false },
      { DataModel.Operator.semVerGreaterThan, LDValue.of("2.0"), LDValue.of("2.0.1"), false },
      { DataModel.Operator.semVerGreaterThan, LDValue.of("2.0.0-rc.1"), LDValue.of("2.0.0-rc.0"), true },
      { DataModel.Operator.semVerLessThan, LDValue.of("2.0.1"), invalidVer, false },
      { DataModel.Operator.semVerGreaterThan, LDValue.of("2.0.1"), invalidVer, false }
    });
  }

  @Test
  public void parameterizedTestComparison() {
    assertEquals(shouldBe, EvaluatorOperators.apply(op, aValue, bValue));
  }
}
