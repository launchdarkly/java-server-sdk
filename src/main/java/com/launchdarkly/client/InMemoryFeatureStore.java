package com.launchdarkly.client;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread-safe, versioned store for {@link com.launchdarkly.client.FeatureRep} objects based on a
 * {@link HashMap}
 *
 */
public class InMemoryFeatureStore implements FeatureStore {

  final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  final Map<String, FeatureRep<?>> features = new HashMap<String, FeatureRep<?>>();
  volatile boolean initialized = false;


  /**
   *
   * Returns the {@link com.launchdarkly.client.FeatureRep} to which the specified key is mapped, or
   * null if the key is not associated or the associated {@link com.launchdarkly.client.FeatureRep} has
   * been deleted.
   *
   * @param key the key whose associated {@link com.launchdarkly.client.FeatureRep} is to be returned
   * @return the {@link com.launchdarkly.client.FeatureRep} to which the specified key is mapped, or
   * null if the key is not associated or the associated {@link com.launchdarkly.client.FeatureRep} has
   * been deleted.
   */
  @Override
  public FeatureRep<?> get(String key) {
    try {
      lock.readLock().lock();
      FeatureRep<?> rep =  features.get(key);
      if (rep == null || rep.deleted) {
        return null;
      }
      return rep;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns a {@link java.util.Map} of all associated features.
   *
   *
   * @return a map of all associated features.
   */
  @Override
  public Map<String, FeatureRep<?>> all() {
    try {
      lock.readLock().lock();
      Map<String, FeatureRep<?>> fs = new HashMap<String, FeatureRep<?>>();

      for (Map.Entry<String, FeatureRep<?>> entry : features.entrySet()) {
        if (!entry.getValue().deleted) {
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
  public void init(Map<String, FeatureRep<?>> features) {
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
   *
   * Deletes the feature associated with the specified key, if it exists and its version
   * is less than or equal to the specified version.
   *
   * @param key the key of the feature to be deleted
   * @param version the version for the delete operation
   */
  @Override
  public void delete(String key, int version) {
    try {
      lock.writeLock().lock();
      FeatureRep<?> f = features.get(key);
      if (f != null && f.version < version) {
        f.deleted = true;
        f.version = version;
        features.put(key, f);
      }
      else if (f == null) {
        f = new FeatureRep.Builder(key, key).deleted(true).version(version).build();
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
  public void upsert(String key, FeatureRep<?> feature) {
    try {
      lock.writeLock().lock();
      FeatureRep<?> old = features.get(key);

      if (old == null || old.version < feature.version) {
        features.put(key, feature);
      }
    }
    finally {
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
}
