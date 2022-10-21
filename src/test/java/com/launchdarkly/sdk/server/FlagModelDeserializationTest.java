package com.launchdarkly.sdk.server;

import com.google.gson.Gson;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Prerequisite;
import com.launchdarkly.sdk.server.DataModel.Rule;
import com.launchdarkly.sdk.server.DataModel.Target;

import org.junit.Test;

import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.target;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings("javadoc")
public class FlagModelDeserializationTest {
  private static final Gson gson = new Gson();
  
  // The details of the preprocessed data are verified by DataModelPreprocessingTest; here we're
  // just verifying that the preprocessing is actually being done whenever we deserialize a flag.
  @Test
  public void preprocessingIsDoneOnDeserialization() {
    FeatureFlag originalFlag = flagBuilder("flagkey")
        .variations("a", "b")
        .prerequisites(new Prerequisite("abc", 0))
        .targets(target(0, "x"))
        .rules(ruleBuilder().clauses(
            clause("key", Operator.in, LDValue.of("x"), LDValue.of("y"))
            ).build())
        .build();
    String flagJson = JsonHelpers.serialize(originalFlag);

    FeatureFlag flag = gson.fromJson(flagJson, FeatureFlag.class);
    assertNotNull(flag.preprocessed);
    for (Prerequisite p: flag.getPrerequisites()) {
      assertNotNull(p.preprocessed);
    }
    for (Target t: flag.getTargets()) {
      assertNotNull(t.preprocessed);
    }
    for (Rule r: flag.getRules()) {
      assertThat(r.preprocessed, notNullValue());
      for (Clause c: r.getClauses()) {
        assertThat(c.preprocessed, notNullValue());
      }
    }
  }
}
