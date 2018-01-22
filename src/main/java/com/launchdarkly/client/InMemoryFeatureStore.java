package com.launchdarkly.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread-safe, versioned store for {@link FeatureFlag} objects based on a
 * {@link HashMap}
 */
public class InMemoryFeatureStore implements FeatureStore {
  private static final Logger logger = LoggerFactory.getLogger(InMemoryFeatureStore.class);

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final Map<VersionedDataKind<?>, Map<String, VersionedData>> allData = new HashMap<>();
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
  public <T extends VersionedData> T get(VersionedDataKind<T> kind, String key) {
    try {
      lock.readLock().lock();
      Map<String, VersionedData> items = allData.get(kind);
      if (items == null) {
        logger.debug("[get] no objects exist for \"{0}\". Returning null", kind.getNamespace());
        return null;
      }
      Object o = items.get(key);
      if (o == null) {
        logger.debug("[get] Key: {0} not found in \"{1}\". Returning null", key, kind.getNamespace());
        return null;
      }
      if (!kind.getItemClass().isInstance(o)) {
        logger.warn("[get] Unexpected object class {0} found for key: {1} in \"{2}\". Returning null",
            o.getClass().getName(), key, kind.getNamespace());
        return null;
      }
      T item = kind.getItemClass().cast(o);
      if (item.isDeleted()) {
        logger.debug("[get] Key: {0} has been deleted. Returning null", key);
        return null;
      }
      logger.debug("[get] Key: {0} with version: {1} found in \"{2}\".", key, item.getVersion(), kind.getNamespace());
      return item;
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
  public <T extends VersionedData> Map<String, T> all(VersionedDataKind<T> kind) {
    try {
      lock.readLock().lock();
      Map<String, T> fs = new HashMap<>();
      Map<String, VersionedData> items = allData.get(kind);
      if (items != null) {
        for (Map.Entry<String, ? extends VersionedData> entry : items.entrySet()) {
          if (!entry.getValue().isDeleted()) {
            fs.put(entry.getKey(), kind.getItemClass().cast(entry.getValue()));
          }
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
  @SuppressWarnings("unchecked")
  @Override
  public void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
    try {
      lock.writeLock().lock();
      this.allData.clear();
      for (Map.Entry<VersionedDataKind<?>, Map<String, ? extends VersionedData>> entry: allData.entrySet()) {
        this.allData.put(entry.getKey(), (Map<String, VersionedData>)entry.getValue());
      }
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
  public <T extends VersionedData> void delete(VersionedDataKind<T> kind, String key, int version) {
    try {
      lock.writeLock().lock();
      Map<String, VersionedData> items = allData.get(kind);
      if (items == null) {
        items = new HashMap<>();
        allData.put(kind, items);
      }
      VersionedData item = items.get(key);
      if (item == null || item.getVersion() < version) {
        items.put(key, kind.makeDeletedItem(key, version));
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
  public <T extends VersionedData> void upsert(VersionedDataKind<T> kind, T item) {
    try {
      lock.writeLock().lock();
      Map<String, VersionedData> items = (Map<String, VersionedData>) allData.get(kind);
      if (items == null) {
        items = new HashMap<>();
        allData.put(kind, items);
      }
      VersionedData old = items.get(item.getKey());

      if (old == null || old.getVersion() < item.getVersion()) {
        items.put(item.getKey(), item);
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
