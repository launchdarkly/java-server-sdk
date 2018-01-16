package com.launchdarkly.client;

import static org.junit.Assert.assertFalse;

import java.util.regex.PatternSyntaxException;

import org.junit.Test;

import com.google.gson.JsonPrimitive;

// Any special-case tests that can't be handled by OperatorParameterizedTest.
public class OperatorTest {
  // This is probably not desired behavior, but it is the current behavior
  @Test(expected = PatternSyntaxException.class)
  public void testInvalidRegexThrowsException() {
    assertFalse(Operator.matches.apply(new JsonPrimitive("hello world"), new JsonPrimitive("***not a regex")));    
  }
}
