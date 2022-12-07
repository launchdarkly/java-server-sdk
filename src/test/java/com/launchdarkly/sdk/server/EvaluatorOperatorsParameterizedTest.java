package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.Operator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.launchdarkly.sdk.server.EvaluatorHelpers.matchClauseWithoutSegments;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class EvaluatorOperatorsParameterizedTest {
  private static final LDValue invalidVer = LDValue.of("xbad%ver");
  
  private static final AttributeRef userAttr = AttributeRef.fromLiteral("attr");
  
  private final Operator op;
  private final LDValue userValue;
  private final LDValue clauseValue;
  private final LDValue[] extraClauseValues;
  private final boolean shouldBe;
  
  public EvaluatorOperatorsParameterizedTest(
      Operator op,
      LDValue userValue,
      LDValue clauseValue,
      LDValue[] extraClauseValues,
      boolean shouldBe
      ) {
    this.op = op;
    this.userValue = userValue;
    this.clauseValue = clauseValue;
    this.extraClauseValues = extraClauseValues;
    this.shouldBe = shouldBe;
  }
  
  @Parameterized.Parameters(name = "{1} {0} {2}+{3} should be {4}")
  public static Iterable<Object[]> data() {
    ImmutableList.Builder<Object[]> tests = ImmutableList.builder();
    
    tests.add(new Object[][] {
      // numeric comparisons
      { Operator.in, LDValue.of(99), LDValue.of(99), null, true },
      { Operator.in, LDValue.of(99), LDValue.of(99), new LDValue[] { LDValue.of(98), LDValue.of(97), LDValue.of(96) }, true },
      { Operator.in, LDValue.of(99.0001), LDValue.of(99.0001), new LDValue[] { LDValue.of(98), LDValue.of(97), LDValue.of(96) }, true },
      { Operator.in, LDValue.of(99.0001), LDValue.of(99.0001), null, true },
      { Operator.in, LDValue.of(99), LDValue.of(99.0001), null, false },
      { Operator.in, LDValue.of(99.0001), LDValue.of(99), null, false },
      { Operator.lessThan, LDValue.of(99), LDValue.of(99.0001), null, true },
      { Operator.lessThan, LDValue.of(99.0001), LDValue.of(99), null, false },
      { Operator.lessThan, LDValue.of(99), LDValue.of(99), null, false },
      { Operator.lessThanOrEqual, LDValue.of(99), LDValue.of(99.0001), null, true },
      { Operator.lessThanOrEqual, LDValue.of(99.0001), LDValue.of(99), null, false },
      { Operator.lessThanOrEqual, LDValue.of(99), LDValue.of(99), null, true },
      { Operator.greaterThan, LDValue.of(99.0001), LDValue.of(99), null, true },
      { Operator.greaterThan, LDValue.of(99), LDValue.of(99.0001), null, false },
      { Operator.greaterThan, LDValue.of(99), LDValue.of(99), null, false },
      { Operator.greaterThanOrEqual, LDValue.of(99.0001), LDValue.of(99), null, true },
      { Operator.greaterThanOrEqual, LDValue.of(99), LDValue.of(99.0001), null, false },
      { Operator.greaterThanOrEqual, LDValue.of(99), LDValue.of(99), null, true },
      
      // string comparisons
      { Operator.in, LDValue.of("x"), LDValue.of("x"), null, true },
      { Operator.in, LDValue.of("x"), LDValue.of("xyz"), null, false },
      { Operator.in, LDValue.of("x"), LDValue.of("x"), new LDValue[] { LDValue.of("a"), LDValue.of("b"), LDValue.of("c") }, true },
      { Operator.startsWith, LDValue.of("xyz"), LDValue.of("x"), null, true },
      { Operator.startsWith, LDValue.of("x"), LDValue.of("xyz"), null, false },
      { Operator.startsWith, LDValue.of(1), LDValue.of("xyz"), null, false },
      { Operator.startsWith, LDValue.of("1xyz"), LDValue.of(1), null, false },
      { Operator.endsWith, LDValue.of("xyz"), LDValue.of("z"), null, true },
      { Operator.endsWith, LDValue.of("z"), LDValue.of("xyz"), null, false },
      { Operator.endsWith, LDValue.of(1), LDValue.of("xyz"), null, false },
      { Operator.endsWith, LDValue.of("xyz1"), LDValue.of(1), null, false },
      { Operator.contains, LDValue.of("xyz"), LDValue.of("y"), null, true },
      { Operator.contains, LDValue.of("y"), LDValue.of("xyz"), null, false },
      { Operator.contains, LDValue.of(2), LDValue.of("xyz"), null, false },
      { Operator.contains, LDValue.of("that 2 is not a string"), LDValue.of(2), null, false },
      
      // mixed strings and numbers
      { Operator.in, LDValue.of("99"), LDValue.of(99), null, false },
      { Operator.in, LDValue.of(99), LDValue.of("99"), null, false },
      { Operator.contains, LDValue.of("99"), LDValue.of(99), null, false },
      { Operator.startsWith, LDValue.of("99"), LDValue.of(99), null, false },
      { Operator.endsWith, LDValue.of("99"), LDValue.of(99), null, false },
      { Operator.lessThanOrEqual, LDValue.of("99"), LDValue.of(99), null, false },
      { Operator.lessThanOrEqual, LDValue.of(99), LDValue.of("99"), null, false },
      { Operator.greaterThanOrEqual, LDValue.of("99"), LDValue.of(99), null, false },
      { Operator.greaterThanOrEqual, LDValue.of(99), LDValue.of("99"), null, false },
      
      // boolean values
      { Operator.in, LDValue.of(true), LDValue.of(true), null, true },
      { Operator.in, LDValue.of(false), LDValue.of(false), null, true },
      { Operator.in, LDValue.of(true), LDValue.of(false), null, false },
      { Operator.in, LDValue.of(false), LDValue.of(true), null, false },
      { Operator.in, LDValue.of(true), LDValue.of(false), new LDValue[] { LDValue.of(true) }, true },
      
      // regex
      { Operator.matches, LDValue.of("hello world"), LDValue.of("hello.*rld"), null, true },
      { Operator.matches, LDValue.of("hello world"), LDValue.of("hello.*orl"), null, true },
      { Operator.matches, LDValue.of("hello world"), LDValue.of("l+"), null, true },
      { Operator.matches, LDValue.of("hello world"), LDValue.of("(world|planet)"), null, true },
      { Operator.matches, LDValue.of("hello world"), LDValue.of("aloha"), null, false },
      // note that an invalid regex in a clause should *not* cause an exception, just a non-match
      { Operator.matches, LDValue.of("hello world"), LDValue.of("***not a regex"), null, false },
      { Operator.matches, LDValue.of(2), LDValue.of("that 2 is not a string"), null, false },
      
      // semver
      { Operator.semVerEqual, LDValue.of("2.0.1"), LDValue.of("2.0.1"), null, true },
      { Operator.semVerEqual, LDValue.of("2.0.2"), LDValue.of("2.0.1"), null, false },
      { Operator.semVerEqual, LDValue.of("2.0"), LDValue.of("2.0.0"), null, true },
      { Operator.semVerEqual, LDValue.of("2"), LDValue.of("2.0.0"), null, true },
      { Operator.semVerEqual, LDValue.of("2-rc1"), LDValue.of("2.0.0-rc1"), null, true },
      { Operator.semVerEqual, LDValue.of("2+build2"), LDValue.of("2.0.0+build2"), null, true },
      { Operator.semVerEqual, LDValue.of("xxx"), LDValue.of("2.0.1"), null, false },
      { Operator.semVerEqual, LDValue.of(2), LDValue.of("2.0.1"), null, false },
      { Operator.semVerEqual, LDValue.of("2.0.1"), LDValue.of("xxx"), null, false },
      { Operator.semVerEqual, LDValue.of("2.0.1"), LDValue.of(2), null, false },
      { Operator.semVerLessThan, LDValue.of("2.0.0"), LDValue.of("2.0.1"), null, true },
      { Operator.semVerLessThan, LDValue.of("2.0"), LDValue.of("2.0.1"), null, true },
      { Operator.semVerLessThan, LDValue.of("2.0.1"), LDValue.of("2.0.0"), null, false },
      { Operator.semVerLessThan, LDValue.of("2.0.1"), LDValue.of("2.0"), null, false },
      { Operator.semVerLessThan, LDValue.of("2.0.0-rc"), LDValue.of("2.0.0"), null, true },
      { Operator.semVerLessThan, LDValue.of("2.0.0-rc"), LDValue.of("2.0.0-rc.beta"), null, true },
      { Operator.semVerGreaterThan, LDValue.of("2.0.1"), LDValue.of("2.0.0"), null, true },
      { Operator.semVerGreaterThan, LDValue.of("2.0.1"), LDValue.of("2.0"), null, true },
      { Operator.semVerGreaterThan, LDValue.of("2.0.0"), LDValue.of("2.0.1"), null, false },
      { Operator.semVerGreaterThan, LDValue.of("2.0"), LDValue.of("2.0.1"), null, false },
      { Operator.semVerGreaterThan, LDValue.of("2.0.0-rc.1"), LDValue.of("2.0.0-rc.0"), null, true },
      { Operator.semVerLessThan, LDValue.of("2.0.1"), invalidVer, null, false },
      { Operator.semVerGreaterThan, LDValue.of("2.0.1"), invalidVer, null, false },
      
      // miscellaneous invalid conditions
      { null, LDValue.of("x"), LDValue.of("y"), null, false }, // no operator
      { Operator.segmentMatch, LDValue.of("x"), LDValue.of("y"), null, false } // segmentMatch is handled elsewhere
    });
    
    // add permutations of date values for before & after operators
    // dateStr1, dateStrUtc1, and dateMs1 are the same timestamp in different formats; etc.
    LDValue dateStr1 = LDValue.of("2017-12-06T00:00:00.000-07:00");
    LDValue dateStrUtc1 = LDValue.of("2017-12-06T07:00:00.000Z");
    LDValue dateMs1 = LDValue.of(1512543600000L);
    LDValue dateStr2 = LDValue.of("2017-12-06T00:00:01.000-07:00");
    LDValue dateStrUtc2 = LDValue.of("2017-12-06T07:00:01.000Z");
    LDValue dateMs2 = LDValue.of(1512543601000L);
    LDValue invalidDate = LDValue.of("hey what's this?");
    for (LDValue lowerValue: new LDValue[] { dateStr1, dateStrUtc1, dateMs1 }) {
      for (LDValue higherValue: new LDValue[] { dateStr2, dateStrUtc2, dateMs2 }) {
        tests.add(new Object[] { Operator.before, lowerValue, higherValue, null, true });
        tests.add(new Object[] { Operator.before, lowerValue, lowerValue, null, false });
        tests.add(new Object[] { Operator.before, higherValue, lowerValue, null, false });
        tests.add(new Object[] { Operator.before, lowerValue, invalidDate, null, false });
        tests.add(new Object[] { Operator.after, higherValue, lowerValue, null, true });
        tests.add(new Object[] { Operator.after, lowerValue, lowerValue, null, false });
        tests.add(new Object[] { Operator.after, lowerValue, higherValue, null, false});
        tests.add(new Object[] { Operator.after, lowerValue, invalidDate, null, false});
      }
    }
    
    return tests.build();
  }

  @Test
  public void parameterizedTestComparison() {
    List<LDValue> values = new ArrayList<>(5);
    if (extraClauseValues != null) {
      values.addAll(Arrays.asList(extraClauseValues));
    }
    values.add(clauseValue);
    
    Clause clause1 = new Clause(null, userAttr, op, values, false);
    assertEquals("without preprocessing", shouldBe, matchClauseWithoutSegments(clause1, userValue));
    
    Clause clause2 = new Clause(null, userAttr, op, values, false);
    DataModelPreprocessing.preprocessClause(clause2);
    assertEquals("without preprocessing", shouldBe, matchClauseWithoutSegments(clause2, userValue));
  }
}
