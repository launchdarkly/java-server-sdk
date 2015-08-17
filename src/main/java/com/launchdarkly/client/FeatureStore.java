package com.launchdarkly.client;

import java.util.Map;

/**
 * Created by jkodumal on 8/13/15.
 */
public interface FeatureStore {
  FeatureRep<?> get(String key);
  Map<String, FeatureRep<?>> all();
  void init(Map<String, FeatureRep<?>> features);
  void delete(String key, int version);
  void upsert(String key, FeatureRep<?> feature);
  boolean initialized();
}
