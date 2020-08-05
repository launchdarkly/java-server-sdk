package com.launchdarkly.sdk.server;

import com.launchdarkly.client.Event;
import com.launchdarkly.client.LDUser;
import com.launchdarkly.client.value.LDValue;

import java.util.ArrayList;
import java.util.List;

public abstract class TestValues {
  private TestValues() {}

  public static final String SDK_KEY = "sdk-key";
  
  public static final LDUser BASIC_USER = new LDUser("userkey");
  
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
  public static final int CLAUSE_MATCH_VALUE_COUNT = 1000;
  public static final List<LDValue> CLAUSE_MATCH_VALUES;
  public static final List<LDUser> CLAUSE_MATCH_VALUE_USERS;
  static {
    // pre-generate all these values and matching users so this work doesn't count in the evaluation benchmark performance
    CLAUSE_MATCH_VALUES = new ArrayList<>(CLAUSE_MATCH_VALUE_COUNT);
    CLAUSE_MATCH_VALUE_USERS = new ArrayList<>(CLAUSE_MATCH_VALUE_COUNT);
    for (int i = 0; i < 1000; i++) {
      LDValue value = LDValue.of("value-" + i);
      LDUser user = new LDUser.Builder("key").custom(CLAUSE_MATCH_ATTRIBUTE, value).build();
      CLAUSE_MATCH_VALUES.add(value);
      CLAUSE_MATCH_VALUE_USERS.add(user);
    }
  }
  public static final LDValue NOT_MATCHED_VALUE = LDValue.of("no-match");
  public static final LDUser NOT_MATCHED_VALUE_USER =
      new LDUser.Builder("key").custom(CLAUSE_MATCH_ATTRIBUTE, NOT_MATCHED_VALUE).build();
  
  public static final String EMPTY_JSON_DATA = "{\"flags\":{},\"segments\":{}}";
  
  public static final int TEST_EVENTS_COUNT = 1000;
  
  public static final LDValue CUSTOM_EVENT_DATA = LDValue.of("data");
  
  public static final Event.Custom CUSTOM_EVENT = new Event.Custom(
      System.currentTimeMillis(),
      "event-key",
      BASIC_USER,
      CUSTOM_EVENT_DATA,
      null
      );
}
