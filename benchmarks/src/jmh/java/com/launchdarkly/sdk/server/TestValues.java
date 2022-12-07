package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Target;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.flagWithValue;
import static com.launchdarkly.sdk.server.ModelBuilders.prerequisite;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;

public abstract class TestValues {
  private TestValues() {}

  public static final String SDK_KEY = "sdk-key";
  
  public static final LDContext BASIC_CONTEXT = LDContext.create("userkey");
  
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
  public static final String NOT_TARGETED_CONTEXT_KEY = "no-match";

  public static final String CLAUSE_MATCH_ATTRIBUTE = "clause-match-attr";
  public static final int CLAUSE_MATCH_VALUE_COUNT = 1000;
  public static final List<LDValue> CLAUSE_MATCH_VALUES;
  public static final List<LDContext> CLAUSE_MATCH_VALUE_CONTEXTS;
  static {
    // pre-generate all these values and matching users so this work doesn't count in the evaluation benchmark performance
    CLAUSE_MATCH_VALUES = new ArrayList<>(CLAUSE_MATCH_VALUE_COUNT);
    CLAUSE_MATCH_VALUE_CONTEXTS = new ArrayList<>(CLAUSE_MATCH_VALUE_COUNT);
    for (int i = 0; i < 1000; i++) {
      LDValue value = LDValue.of("value-" + i);
      LDContext context = LDContext.builder("key").set(CLAUSE_MATCH_ATTRIBUTE, value).build();
      CLAUSE_MATCH_VALUES.add(value);
      CLAUSE_MATCH_VALUE_CONTEXTS.add(context);
    }
  }
  public static final LDValue NOT_MATCHED_VALUE = LDValue.of("no-match");
  public static final LDContext NOT_MATCHED_VALUE_CONTEXT =
      LDContext.builder("key").set(CLAUSE_MATCH_ATTRIBUTE, NOT_MATCHED_VALUE).build();
  
  public static final String EMPTY_JSON_DATA = "{\"flags\":{},\"segments\":{}}";
  
  public static List<FeatureFlag> makeTestFlags() {
    List<FeatureFlag> flags = new ArrayList<>();

    flags.add(flagWithValue(BOOLEAN_FLAG_KEY, LDValue.of(true)));
    flags.add(flagWithValue(INT_FLAG_KEY, LDValue.of(1)));
    flags.add(flagWithValue(STRING_FLAG_KEY, LDValue.of("x")));
    flags.add(flagWithValue(JSON_FLAG_KEY, LDValue.buildArray().build()));

    FeatureFlag targetsFlag = flagBuilder(FLAG_WITH_TARGET_LIST_KEY)
      .on(true)
      .targets(new Target(null, new HashSet<String>(TARGETED_USER_KEYS), 1))
      .fallthroughVariation(0)
      .offVariation(0)
      .variations(LDValue.of(false), LDValue.of(true))
      .build();
    flags.add(targetsFlag);

    FeatureFlag prereqFlag = flagBuilder("prereq-flag")
      .on(true)
      .fallthroughVariation(1)
      .variations(LDValue.of(false), LDValue.of(true))
      .build();
    flags.add(prereqFlag);

    FeatureFlag flagWithPrereq = flagBuilder(FLAG_WITH_PREREQ_KEY)
      .on(true)
      .prerequisites(prerequisite("prereq-flag", 1))
      .fallthroughVariation(1)
      .offVariation(0)
      .variations(LDValue.of(false), LDValue.of(true))
      .build();
    flags.add(flagWithPrereq);

    AttributeRef matchAttr = AttributeRef.fromLiteral(CLAUSE_MATCH_ATTRIBUTE);
    FeatureFlag flagWithMultiValueClause = flagBuilder(FLAG_WITH_MULTI_VALUE_CLAUSE_KEY)
      .on(true)
      .fallthroughVariation(0)
      .offVariation(0)
      .variations(LDValue.of(false), LDValue.of(true))
      .rules(
          ruleBuilder()
            .clauses(new DataModel.Clause(null, matchAttr, DataModel.Operator.in, CLAUSE_MATCH_VALUES, false))
            .build()
          )
      .build();
    flags.add(flagWithMultiValueClause);
    
    return flags;
  }

  public static final int TEST_EVENTS_COUNT = 1000;
  
  public static final LDValue CUSTOM_EVENT_DATA = LDValue.of("data");
}
