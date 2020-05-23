package com.launchdarkly.sdk.server;

import com.launchdarkly.client.Components;
import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.FlagData;
import com.launchdarkly.client.LDClient;
import com.launchdarkly.client.LDClientInterface;
import com.launchdarkly.client.LDConfig;
import com.launchdarkly.client.LDUser;
import com.launchdarkly.client.TestUtil;
import com.launchdarkly.client.value.LDValue;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.Random;

import static com.launchdarkly.sdk.server.TestValues.BOOLEAN_FLAG_KEY;
import static com.launchdarkly.sdk.server.TestValues.CLAUSE_MATCH_VALUE_COUNT;
import static com.launchdarkly.sdk.server.TestValues.FLAG_WITH_MULTI_VALUE_CLAUSE_KEY;
import static com.launchdarkly.sdk.server.TestValues.FLAG_WITH_PREREQ_KEY;
import static com.launchdarkly.sdk.server.TestValues.FLAG_WITH_TARGET_LIST_KEY;
import static com.launchdarkly.sdk.server.TestValues.INT_FLAG_KEY;
import static com.launchdarkly.sdk.server.TestValues.JSON_FLAG_KEY;
import static com.launchdarkly.sdk.server.TestValues.NOT_MATCHED_VALUE_USER;
import static com.launchdarkly.sdk.server.TestValues.NOT_TARGETED_USER_KEY;
import static com.launchdarkly.sdk.server.TestValues.SDK_KEY;
import static com.launchdarkly.sdk.server.TestValues.STRING_FLAG_KEY;
import static com.launchdarkly.sdk.server.TestValues.TARGETED_USER_KEYS;
import static com.launchdarkly.sdk.server.TestValues.UNKNOWN_FLAG_KEY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * These benchmarks cover just the evaluation logic itself (and, by necessity, the overhead of getting the
 * flag to be evaluated out of the in-memory store).
 */
public class LDClientEvaluationBenchmarks {
  @State(Scope.Thread)
  public static class BenchmarkInputs {
    // Initialization of the things in BenchmarkInputs does not count as part of a benchmark.
    final LDClientInterface client;
    final LDUser basicUser;
    final Random random;

    public BenchmarkInputs() {
      FeatureStore featureStore = TestUtil.initedFeatureStore();
      FlagData.loadTestFlags(featureStore);

      LDConfig config = new LDConfig.Builder()
        .dataStore(TestUtil.specificFeatureStore(featureStore))
        .events(Components.noEvents())
        .dataSource(Components.externalUpdatesOnly())
        .build();
      client = new LDClient(SDK_KEY, config);

      basicUser = new LDUser("userkey");

      random = new Random();
    }
  }

  @Benchmark
  public void boolVariationForSimpleFlag(BenchmarkInputs inputs) throws Exception {
    inputs.client.boolVariation(BOOLEAN_FLAG_KEY, inputs.basicUser, false);
  }

  @Benchmark
  public void boolVariationDetailForSimpleFlag(BenchmarkInputs inputs) throws Exception {
    inputs.client.boolVariationDetail(BOOLEAN_FLAG_KEY, inputs.basicUser, false);
  }

  @Benchmark
  public void boolVariationForUnknownFlag(BenchmarkInputs inputs) throws Exception {
    inputs.client.boolVariation(UNKNOWN_FLAG_KEY, inputs.basicUser, false);
  }

  @Benchmark
  public void intVariationForSimpleFlag(BenchmarkInputs inputs) throws Exception {
    inputs.client.intVariation(INT_FLAG_KEY, inputs.basicUser, 0);
  }

  @Benchmark
  public void intVariationDetailForSimpleFlag(BenchmarkInputs inputs) throws Exception {
    inputs.client.intVariationDetail(INT_FLAG_KEY, inputs.basicUser, 0);
  }

  @Benchmark
  public void intVariationForUnknownFlag(BenchmarkInputs inputs) throws Exception {
    inputs.client.intVariation(UNKNOWN_FLAG_KEY, inputs.basicUser, 0);
  }

  @Benchmark
  public void stringVariationForSimpleFlag(BenchmarkInputs inputs) throws Exception {
    inputs.client.stringVariation(STRING_FLAG_KEY, inputs.basicUser, "");
  }

  @Benchmark
  public void stringVariationDetailForSimpleFlag(BenchmarkInputs inputs) throws Exception {
    inputs.client.stringVariationDetail(STRING_FLAG_KEY, inputs.basicUser, "");
  }

  @Benchmark
  public void stringVariationForUnknownFlag(BenchmarkInputs inputs) throws Exception {
    inputs.client.stringVariation(UNKNOWN_FLAG_KEY, inputs.basicUser, "");
  }

  @Benchmark
  public void jsonVariationForSimpleFlag(BenchmarkInputs inputs) throws Exception {
    inputs.client.jsonValueVariation(JSON_FLAG_KEY, inputs.basicUser, LDValue.ofNull());
  }

  @Benchmark
  public void jsonVariationDetailForSimpleFlag(BenchmarkInputs inputs) throws Exception {
    inputs.client.jsonValueVariationDetail(JSON_FLAG_KEY, inputs.basicUser, LDValue.ofNull());
  }

  @Benchmark
  public void jsonVariationForUnknownFlag(BenchmarkInputs inputs) throws Exception {
    inputs.client.jsonValueVariation(UNKNOWN_FLAG_KEY, inputs.basicUser, LDValue.ofNull());
  }

  @Benchmark
  public void userFoundInTargetList(BenchmarkInputs inputs) throws Exception {
    String userKey = TARGETED_USER_KEYS.get(inputs.random.nextInt(TARGETED_USER_KEYS.size()));
    boolean result = inputs.client.boolVariation(FLAG_WITH_TARGET_LIST_KEY, new LDUser(userKey), false);
    assertTrue(result);
  }

  @Benchmark
  public void userNotFoundInTargetList(BenchmarkInputs inputs) throws Exception {
    boolean result = inputs.client.boolVariation(FLAG_WITH_TARGET_LIST_KEY, new LDUser(NOT_TARGETED_USER_KEY), false);
    assertFalse(result);
  }

  @Benchmark
  public void flagWithPrerequisite(BenchmarkInputs inputs) throws Exception {
    boolean result = inputs.client.boolVariation(FLAG_WITH_PREREQ_KEY, inputs.basicUser, false);
    assertTrue(result);
  }
  
  @Benchmark
  public void userValueFoundInClauseList(BenchmarkInputs inputs) throws Exception {
    int i = inputs.random.nextInt(CLAUSE_MATCH_VALUE_COUNT);
    LDUser user = TestValues.CLAUSE_MATCH_VALUE_USERS.get(i);
    boolean result = inputs.client.boolVariation(FLAG_WITH_MULTI_VALUE_CLAUSE_KEY, user, false);
    assertTrue(result);
  }
  
  @Benchmark
  public void userValueNotFoundInClauseList(BenchmarkInputs inputs) throws Exception {
    boolean result = inputs.client.boolVariation(FLAG_WITH_MULTI_VALUE_CLAUSE_KEY, NOT_MATCHED_VALUE_USER, false);
    assertFalse(result);
  }
}
