package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.launchdarkly.client.TestUtil.fallthroughVariation;
import static com.launchdarkly.client.TestUtil.flagWithValue;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.sdk.server.TestValues.BOOLEAN_FLAG_KEY;
import static com.launchdarkly.sdk.server.TestValues.CLAUSE_MATCH_ATTRIBUTE;
import static com.launchdarkly.sdk.server.TestValues.CLAUSE_MATCH_VALUES;
import static com.launchdarkly.sdk.server.TestValues.FLAG_WITH_MULTI_VALUE_CLAUSE_KEY;
import static com.launchdarkly.sdk.server.TestValues.FLAG_WITH_PREREQ_KEY;
import static com.launchdarkly.sdk.server.TestValues.FLAG_WITH_TARGET_LIST_KEY;
import static com.launchdarkly.sdk.server.TestValues.INT_FLAG_KEY;
import static com.launchdarkly.sdk.server.TestValues.JSON_FLAG_KEY;
import static com.launchdarkly.sdk.server.TestValues.STRING_FLAG_KEY;
import static com.launchdarkly.sdk.server.TestValues.TARGETED_USER_KEYS;

// This class must be in com.launchdarkly.client because FeatureFlagBuilder is package-private in the
// SDK, but we are keeping the rest of the benchmark implementation code in com.launchdarkly.sdk.server
// so we can more clearly compare between 4.x and 5.0.
public class FlagData {
  public static void loadTestFlags(FeatureStore store) {
    for (FeatureFlag flag: FlagData.makeTestFlags()) {
      store.upsert(FEATURES, flag);
    }
  }
  
  public static List<FeatureFlag> makeTestFlags() {
    List<FeatureFlag> flags = new ArrayList<>();

    flags.add(flagWithValue(BOOLEAN_FLAG_KEY, LDValue.of(true)));
    flags.add(flagWithValue(INT_FLAG_KEY, LDValue.of(1)));
    flags.add(flagWithValue(STRING_FLAG_KEY, LDValue.of("x")));
    flags.add(flagWithValue(JSON_FLAG_KEY, LDValue.buildArray().build()));

    FeatureFlag targetsFlag = new FeatureFlagBuilder(FLAG_WITH_TARGET_LIST_KEY)
      .on(true)
      .targets(Arrays.asList(new Target(new HashSet<String>(TARGETED_USER_KEYS), 1)))
      .fallthrough(fallthroughVariation(0))
      .offVariation(0)
      .variations(LDValue.of(false), LDValue.of(true))
      .build();
    flags.add(targetsFlag);

    FeatureFlag prereqFlag = new FeatureFlagBuilder("prereq-flag")
      .on(true)
      .fallthrough(fallthroughVariation(1))
      .variations(LDValue.of(false), LDValue.of(true))
      .build();
    flags.add(prereqFlag);

    FeatureFlag flagWithPrereq = new FeatureFlagBuilder(FLAG_WITH_PREREQ_KEY)
      .on(true)
      .prerequisites(Arrays.asList(new Prerequisite("prereq-flag", 1)))
      .fallthrough(fallthroughVariation(1))
      .offVariation(0)
      .variations(LDValue.of(false), LDValue.of(true))
      .build();
    flags.add(flagWithPrereq);

    FeatureFlag flagWithMultiValueClause = new FeatureFlagBuilder(FLAG_WITH_MULTI_VALUE_CLAUSE_KEY)
      .on(true)
      .fallthrough(fallthroughVariation(0))
      .offVariation(0)
      .variations(LDValue.of(false), LDValue.of(true))
      .rules(Arrays.asList(
          new RuleBuilder()
            .clauses(new Clause(CLAUSE_MATCH_ATTRIBUTE, Operator.in, CLAUSE_MATCH_VALUES, false))
            .build()
          ))
      .build();
    flags.add(flagWithMultiValueClause);
    
    return flags;
  }
}
