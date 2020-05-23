package com.launchdarkly.sdk.server;

import com.launchdarkly.client.value.LDValue;

import java.util.ArrayList;
import java.util.List;

public abstract class TestValues {
  private TestValues() {}

  public static final String SDK_KEY = "sdk-key";
  
  public static final String BOOLEAN_FLAG_KEY = "flag-bool";
  public static final String INT_FLAG_KEY = "flag-int";
  public static final String STRING_FLAG_KEY = "flag-string";
  public static final String JSON_FLAG_KEY = "flag-json";
  public static final String FLAG_WITH_TARGET_LIST_KEY = "flag-with-targets";
  public static final String FLAG_WITH_PREREQ_KEY = "flag-with-prereq";
  public static final String FLAG_WITH_MULTI_VALUE_CLAUSE_KEY = "flag-with-multi-value-clause";
  public static final String UNKNOWN_FLAG_KEY = "no-such-flag";

  public static final List<String> TARGETED_USER_KEYS;
  static {
    TARGETED_USER_KEYS = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      TARGETED_USER_KEYS.add("user-" + i);
    }
  }
  public static final String NOT_TARGETED_USER_KEY = "no-match";

  public static final String CLAUSE_MATCH_ATTRIBUTE = "clause-match-attr";
  public static final List<LDValue> CLAUSE_MATCH_VALUES;
  static {
    CLAUSE_MATCH_VALUES = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      CLAUSE_MATCH_VALUES.add(LDValue.of("value-" + i));
    }
  }
  public static final LDValue NOT_MATCHED_VALUE = LDValue.of("no-match");
  
  public static final String EMPTY_JSON_DATA = "{\"flags\":{},\"segments\":{}}";
}
