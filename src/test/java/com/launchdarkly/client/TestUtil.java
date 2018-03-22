package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;

import java.util.Arrays;

public class TestUtil {

  public static JsonPrimitive js(String s) {
    return new JsonPrimitive(s);
  }

  public static JsonPrimitive jint(int n) {
    return new JsonPrimitive(n);
  }
  
  public static JsonPrimitive jbool(boolean b) {
    return new JsonPrimitive(b);
  }
  
  public static VariationOrRollout fallthroughVariation(int variation) {
    return new VariationOrRollout(variation, null);
  }
  
  public static FeatureFlag booleanFlagWithClauses(Clause... clauses) {
    Rule rule = new Rule(Arrays.asList(clauses), 1, null);
    return new FeatureFlagBuilder("feature")
        .on(true)
        .rules(Arrays.asList(rule))
        .fallthrough(fallthroughVariation(0))
        .offVariation(0)
        .variations(jbool(false), jbool(true))
        .build();
  }
}
