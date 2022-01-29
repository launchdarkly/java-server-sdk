package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDValue;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

abstract class EvaluatorTypeConversion {
  private EvaluatorTypeConversion() {}
  
  static Instant valueToDateTime(LDValue value) {
    if (value.isNumber()) {
      return Instant.ofEpochMilli(value.longValue());
    } else if (value.isString()) {
      try {
        return ZonedDateTime.parse(value.stringValue()).toInstant();
      } catch (Throwable t) {
        return null;
      }
    } else {
      return null;
    }
  }
  
  static Pattern valueToRegex(LDValue value) {
    if (!value.isString()) {
      return null;
    }
    try {
      return Pattern.compile(value.stringValue());
    } catch (PatternSyntaxException e) {
      return null;
    }
  }
  
  static SemanticVersion valueToSemVer(LDValue value) {
    if (!value.isString()) {
      return null;
    }
    try {
      return SemanticVersion.parse(value.stringValue(), true);
    } catch (SemanticVersion.InvalidVersionException e) {
      return null;
    }
  }
}
