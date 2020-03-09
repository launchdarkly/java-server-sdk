package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel;
import com.launchdarkly.sdk.server.EvaluatorOperators;

import org.junit.Test;

import java.util.regex.PatternSyntaxException;

import static org.junit.Assert.assertFalse;

// Any special-case tests that can't be handled by EvaluatorOperatorsParameterizedTest.
@SuppressWarnings("javadoc")
public class EvaluatorOperatorsTest {
  // This is probably not desired behavior, but it is the current behavior
  @Test(expected = PatternSyntaxException.class)
  public void testInvalidRegexThrowsException() {
    assertFalse(EvaluatorOperators.apply(DataModel.Operator.matches, LDValue.of("hello world"), LDValue.of("***not a regex")));    
  }
}
