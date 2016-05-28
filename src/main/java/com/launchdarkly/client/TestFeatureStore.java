package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A decorated {@link InMemoryFeatureStore} which provides functionality to create (or override) true or false feature flags for all users.
 * <p>
 * Using this store is useful for testing purposes when you want to have runtime support for turning specific features on or off.
 */
public class TestFeatureStore extends InMemoryFeatureStore {
  private static List<JsonElement> TRUE_FALSE_VARIATIONS = Arrays.asList(
      (JsonElement) (new JsonPrimitive(true)),
      (JsonElement) (new JsonPrimitive(false))
  );

  private AtomicInteger version = new AtomicInteger(0);

  /**
   * Turns a feature, identified by key, to evaluate to true for every user. If the feature rules already exist in the store then it will override it to be true for every {@link LDUser}.
   * If the feature rule is not currently in the store, it will create one that is true for every {@link LDUser}.
   *
   * @param key the key of the feature flag to evaluate to true.
   */
  public void setFeatureTrue(String key) {
    FeatureFlag newFeature = new FeatureFlagBuilder(key)
        .on(false)
        .offVariation(0)
        .variations(TRUE_FALSE_VARIATIONS)
        .version(version.incrementAndGet())
        .build();
    upsert(key, newFeature);
  }

  /**
   * Turns a feature, identified by key, to evaluate to false for every user. If the feature rules already exist in the store then it will override it to be false for every {@link LDUser}.
   * If the feature rule is not currently in the store, it will create one that is false for every {@link LDUser}.
   *
   * @param key the key of the feature flag to evaluate to false.
   */
  public void setFeatureFalse(String key) {
    FeatureFlag newFeature = new FeatureFlagBuilder(key)
        .on(false)
        .offVariation(1)
        .variations(TRUE_FALSE_VARIATIONS)
        .version(version.incrementAndGet())
        .build();
    upsert(key, newFeature);
  }
}
