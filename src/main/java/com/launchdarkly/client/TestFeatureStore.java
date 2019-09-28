package com.launchdarkly.client;

import static com.launchdarkly.client.VersionedDataKind.FEATURES;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.launchdarkly.client.value.LDValue;

/**
 * A decorated {@link InMemoryFeatureStore} which provides functionality to create (or override) true or false feature flags for all users.
 * <p>
 * Using this store is useful for testing purposes when you want to have runtime support for turning specific features on or off.
 *
 * @deprecated Will be replaced by a file-based test fixture.
 */
@Deprecated
public class TestFeatureStore extends InMemoryFeatureStore {
  static List<LDValue> TRUE_FALSE_VARIATIONS = Arrays.asList(LDValue.of(true), LDValue.of(false));

  private AtomicInteger version = new AtomicInteger(0);
  private volatile boolean initializedForTests = false;
  
  /**
   * Sets the value of a boolean feature flag for all users.
   *
   * @param key the key of the feature flag
   * @param value the new value of the feature flag
   * @return the feature flag
   */
  public FeatureFlag setBooleanValue(String key, Boolean value) {
    FeatureFlag newFeature = new FeatureFlagBuilder(key)
            .on(false)
            .offVariation(value ? 0 : 1)
            .variations(TRUE_FALSE_VARIATIONS)
            .version(version.incrementAndGet())
            .build();
    upsert(FEATURES, newFeature);
    return newFeature;
  }

  /**
   * Turns a feature, identified by key, to evaluate to true for every user. If the feature rules already exist in the store then it will override it to be true for every {@link LDUser}.
   * If the feature rule is not currently in the store, it will create one that is true for every {@link LDUser}.
   *
   * @param key the key of the feature flag to evaluate to true
   * @return the feature flag
   */
  public FeatureFlag setFeatureTrue(String key) {
    return setBooleanValue(key, true);
  }
  
  /**
   * Turns a feature, identified by key, to evaluate to false for every user. If the feature rules already exist in the store then it will override it to be false for every {@link LDUser}.
   * If the feature rule is not currently in the store, it will create one that is false for every {@link LDUser}.
   *
   * @param key the key of the feature flag to evaluate to false
   * @return the feature flag
   */
  public FeatureFlag setFeatureFalse(String key) {
    return setBooleanValue(key, false);
  }
  
  /**
   * Sets the value of an integer multivariate feature flag, for all users.
   * @param key the key of the flag
   * @param value the new value of the flag
   * @return the feature flag
     */
  public FeatureFlag setIntegerValue(String key, Integer value) {
    return setJsonValue(key, new JsonPrimitive(value));
  }

  /**
   * Sets the value of a double multivariate feature flag, for all users.
   * @param key the key of the flag
   * @param value the new value of the flag
   * @return the feature flag
     */
  public FeatureFlag setDoubleValue(String key, Double value) {
    return setJsonValue(key, new JsonPrimitive(value));
  }

  /**
   * Sets the value of a string multivariate feature flag, for all users.
   * @param key the key of the flag
   * @param value the new value of the flag
   * @return the feature flag
     */
  public FeatureFlag setStringValue(String key, String value) {
    return setJsonValue(key, new JsonPrimitive(value));
  }

  /**
   * Sets the value of a JsonElement multivariate feature flag, for all users.
   * @param key the key of the flag
   * @param value the new value of the flag
   * @return the feature flag
     */
  public FeatureFlag setJsonValue(String key, JsonElement value) {
    FeatureFlag newFeature = new FeatureFlagBuilder(key)
            .on(false)
            .offVariation(0)
            .variations(Arrays.asList(LDValue.fromJsonElement(value)))
            .version(version.incrementAndGet())
            .build();
    upsert(FEATURES, newFeature);
    return newFeature;
  }
  
  @Override
  public void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
    super.init(allData);
    initializedForTests = true;
  }
  
  @Override
  public boolean initialized() {
    return initializedForTests;
  }
  
  public void setInitialized(boolean value) {
    initializedForTests = value;
  }
}
