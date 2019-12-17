package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.FeatureStore;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread-safe, versioned store for feature flags and related data based on a
 * {@link HashMap}. This is the default implementation of {@link FeatureStore}.
 */
public class InMemoryFeatureStore implements FeatureStore {
  private static final Logger logger = LoggerFactory.getLogger(InMemoryFeatureStore.class);

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final Map<VersionedDataKind<?>, Map<String, VersionedData>> allData = new HashMap<>();
  private volatile boolean initialized = false;

  @Override
  public <T extends VersionedData> T get(VersionedDataKind<T> kind, String key) {
    try {
      lock.readLock().lock();
      Map<String, VersionedData> items = allData.get(kind);
      if (items == null) {
        logger.debug("[get] no objects exist for \"{}\". Returning null", kind.getNamespace());
        return null;
      }
      Object o = items.get(key);
      if (o == null) {
        logger.debug("[get] Key: {} not found in \"{}\". Returning null", key, kind.getNamespace());
        return null;
      }
      if (!kind.getItemClass().isInstance(o)) {
        logger.warn("[get] Unexpected object class {} found for key: {} in \"{}\". Returning null",
            o.getClass().getName(), key, kind.getNamespace());
        return null;
      }
      T item = kind.getItemClass().cast(o);
      if (item.isDeleted()) {
        logger.debug("[get] Key: {} has been deleted. Returning null", key);
        return null;
      }
      logger.debug("[get] Key: {} with version: {} found in \"{}\".", key, item.getVersion(), kind.getNamespace());
      return item;
    } finally {
      lock.readLock().unlock();
    }
  }

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

  @Override
  public void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
    try {
      lock.writeLock().lock();
      this.allData.clear();
      for (Map.Entry<VersionedDataKind<?>, Map<String, ? extends VersionedData>> entry: allData.entrySet()) {
        // Note, the FeatureStore contract specifies that we should clone all of the maps. This doesn't
        // really make a difference in regular use of the SDK, but not doing it could cause unexpected
        // behavior in tests.
        this.allData.put(entry.getKey(), new HashMap<String, VersionedData>(entry.getValue()));
      }
      initialized = true;
    } finally {
      lock.writeLock().unlock();
    }
  }

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

  @Override
  public boolean initialized() {
    return initialized;
  }

  /**
   * Does nothing; this class does not have any resources to release
   *
   * @throws IOException will never happen
   */
  @Override
  public void close() throws IOException {
    return;
  }
}
