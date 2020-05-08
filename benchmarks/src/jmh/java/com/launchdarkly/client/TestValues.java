package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.launchdarkly.client.TestUtil.fallthroughVariation;
import static com.launchdarkly.client.TestUtil.flagWithValue;

public abstract class TestValues {
  private TestValues() {}

  public static final String SDK_KEY = "sdk-key";
  
  public static final String BOOLEAN_FLAG_KEY = "flag-bool";
  public static final String INT_FLAG_KEY = "flag-int";
  public static final String STRING_FLAG_KEY = "flag-string";
  public static final String JSON_FLAG_KEY = "flag-json";
  public static final String FLAG_WITH_TARGET_LIST_KEY = "flag-with-targets";
  public static final String FLAG_WITH_PREREQ_KEY = "flag-with-prereq";
  public static final String UNKNOWN_FLAG_KEY = "no-such-flag";

  public static final List<String> TARGETED_USER_KEYS;
  static {
    TARGETED_USER_KEYS = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      TARGETED_USER_KEYS.add("user-" + i);
    }
  }
  public static final String NOT_TARGETED_USER_KEY = "no-match";

  public static final String EMPTY_JSON_DATA = "{\"flags\":{},\"segments\":{}}";
  
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

    return flags;
  }
}
