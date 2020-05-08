package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.Random;

import static com.launchdarkly.client.TestValues.BOOLEAN_FLAG_KEY;
import static com.launchdarkly.client.TestValues.FLAG_WITH_PREREQ_KEY;
import static com.launchdarkly.client.TestValues.FLAG_WITH_TARGET_LIST_KEY;
import static com.launchdarkly.client.TestValues.INT_FLAG_KEY;
import static com.launchdarkly.client.TestValues.JSON_FLAG_KEY;
import static com.launchdarkly.client.TestValues.NOT_TARGETED_USER_KEY;
import static com.launchdarkly.client.TestValues.SDK_KEY;
import static com.launchdarkly.client.TestValues.STRING_FLAG_KEY;
import static com.launchdarkly.client.TestValues.TARGETED_USER_KEYS;
import static com.launchdarkly.client.TestValues.UNKNOWN_FLAG_KEY;
import static com.launchdarkly.client.TestValues.makeTestFlags;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;

/**
 * These benchmarks cover just the evaluation logic itself (and, by necessity, the overhead of getting the
 * flag to be evaluated out of the in-memory store).
 */
public class LDClientEvaluationBenchmarks {
  @State(Scope.Thread)
  public static class BenchmarkInputs {
    // Initialization of the things in BenchmarkInputs do not count as part of a benchmark.
    final LDClientInterface client;
    final LDUser basicUser;
    final Random random;

    public BenchmarkInputs() {
      FeatureStore featureStore = TestUtil.initedFeatureStore();
      for (FeatureFlag flag: makeTestFlags()) {
        featureStore.upsert(FEATURES, flag);
      }

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
    inputs.client.boolVariation(FLAG_WITH_TARGET_LIST_KEY, new LDUser(userKey), false);
  }

  @Benchmark
  public void userNotFoundInTargetList(BenchmarkInputs inputs) throws Exception {
    inputs.client.boolVariation(FLAG_WITH_TARGET_LIST_KEY, new LDUser(NOT_TARGETED_USER_KEY), false);
  }

  @Benchmark
  public void flagWithPrerequisite(BenchmarkInputs inputs) throws Exception {
    inputs.client.boolVariation(FLAG_WITH_PREREQ_KEY, inputs.basicUser, false);
  }
}
