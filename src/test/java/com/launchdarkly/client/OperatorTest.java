package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import java.util.regex.PatternSyntaxException;

import static org.junit.Assert.assertFalse;

// Any special-case tests that can't be handled by OperatorParameterizedTest.
@SuppressWarnings("javadoc")
public class OperatorTest {
  // This is probably not desired behavior, but it is the current behavior
  @Test(expected = PatternSyntaxException.class)
  public void testInvalidRegexThrowsException() {
    assertFalse(Operator.matches.apply(LDValue.of("hello world"), LDValue.of("***not a regex")));    
  }
}
