package com.launchdarkly.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread-safe, versioned store for {@link FeatureFlag} objects based on a
 * {@link HashMap}
 */
public class InMemoryFeatureStore implements FeatureStore {

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final Map<String, FeatureFlag> features = new HashMap<>();
  private volatile boolean initialized = false;


  /**
   * Returns the {@link FeatureFlag} to which the specified key is mapped, or
   * null if the key is not associated or the associated {@link FeatureFlag} has
   * been deleted.
   *
   * @param key the key whose associated {@link FeatureFlag} is to be returned
   * @return the {@link FeatureFlag} to which the specified key is mapped, or
   * null if the key is not associated or the associated {@link FeatureFlag} has
   * been deleted.
   */
  @Override
  public FeatureFlag get(String key) {
    try {
      lock.readLock().lock();
      FeatureFlag featureFlag = features.get(key);
      if (featureFlag == null || featureFlag.isDeleted()) {
        return null;
      }
      return featureFlag;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns a {@link java.util.Map} of all associated features.
   *
   * @return a map of all associated features.
   */
  @Override
  public Map<String, FeatureFlag> all() {
    try {
      lock.readLock().lock();
      Map<String, FeatureFlag> fs = new HashMap<>();

      for (Map.Entry<String, FeatureFlag> entry : features.entrySet()) {
        if (!entry.getValue().isDeleted()) {
          fs.put(entry.getKey(), entry.getValue());
        }
      }
      return fs;
    } finally {
      lock.readLock().unlock();
    }
  }


  /**
   * Initializes (or re-initializes) the store with the specified set of features. Any existing entries
   * will be removed.
   *
   * @param features the features to set the store
   */
  @Override
  public void init(Map<String, FeatureFlag> features) {
    try {
      lock.writeLock().lock();
      this.features.clear();
      this.features.putAll(features);
      initialized = true;
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Deletes the feature associated with the specified key, if it exists and its version
   * is less than or equal to the specified version.
   *
   * @param key     the key of the feature to be deleted
   * @param version the version for the delete operation
   */
  @Override
  public void delete(String key, int version) {
    try {
      lock.writeLock().lock();
      FeatureFlag f = features.get(key);
      if (f != null && f.getVersion() < version) {
        FeatureFlagBuilder newBuilder = new FeatureFlagBuilder(f);
        newBuilder.on(false);
        newBuilder.version(version);
        features.put(key, newBuilder.build());
      } else if (f == null) {
        f = new FeatureFlagBuilder(key)
            .deleted(true)
            .version(version)
            .build();
        features.put(key, f);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Update or insert the feature associated with the specified key, if its version
   * is less than or equal to the version specified in the argument feature.
   *
   * @param key
   * @param feature
   */
  @Override
  public void upsert(String key, FeatureFlag feature) {
    try {
      lock.writeLock().lock();
      FeatureFlag old = features.get(key);

      if (old == null || old.getVersion() < feature.getVersion()) {
        features.put(key, feature);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Returns true if this store has been initialized
   *
   * @return true if this store has been initialized
   */
  @Override
  public boolean initialized() {
    return initialized;
  }

  /**
   * Does nothing; this class does not have any resources to release
   *
   * @throws IOException
   */
  @Override
  public void close() throws IOException {
    return;
  }
}
