package com.launchdarkly.client;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by jkodumal on 8/13/15.
 */
public class InMemoryFeatureStore implements FeatureStore {

  final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  final Map<String, FeatureRep<?>> features = new HashMap<String, FeatureRep<?>>();
  boolean initialized = false;

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

  @Override
  public void init(Map<String, FeatureRep<?>> fs) {
    try {
      lock.writeLock().lock();
      this.features.clear();
      this.features.putAll(fs);
      initialized = true;
    } finally {
      lock.writeLock().unlock();
    }
  }

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

  @Override
  public boolean initialized() {
    return initialized;
  }
}
