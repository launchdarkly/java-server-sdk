package com.launchdarkly.client;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A decorated {@link InMemoryFeatureStore} which provides functionality to create (or override) "on" or "off" feature flags for all users.
 *
 * Using this store is useful for testing purposes when you want to have runtime support for turning specific features "on" or "off".
 *
 */
public class TestFeatureStore extends InMemoryFeatureStore {

    private AtomicInteger version = new AtomicInteger(0);

    /**
     * Turns a feature, identified by key, "on" for every user. If the feature rules already exist in the store then it will override it to be "on" for every {@link LDUser}.
     * If the feature rule is not currently in the store, it will create one that is "on" for every {@link LDUser}.
     *
     * @param key the key of the feature flag to be "on".
     */
    public void turnFeatureOn(String key) {
        writeFeatureRep(key, new Variation.Builder<>(true, 100).build());
    }

    /**
     * Turns a feature, identified by key, "off" for every user. If the feature rules already exists in the store then it will override it to be "off" for every {@link LDUser}.
     * If the feature rule is not currently in the store, it will create one that is "off" for every {@link LDUser}.
     *
     * @param key the key of the feature flag to be "off".
     */
    public void turnFeatureOff(String key) {
        writeFeatureRep(key, new Variation.Builder<>(false, 100).build());
    }

    private void writeFeatureRep(final String key, final Variation<Boolean> variation) {
        FeatureRep<Boolean> newFeature = new FeatureRep.Builder<Boolean>(String.format("test-%s", key), key)
                .variation(variation).version(version.incrementAndGet()).build();
        upsert(key, newFeature);
    }
}
