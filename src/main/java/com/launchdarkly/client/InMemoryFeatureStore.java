package com.launchdarkly.client;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.client.interfaces.DiagnosticDescription;
import com.launchdarkly.client.value.LDValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A thread-safe, versioned store for feature flags and related data based on a
 * {@link HashMap}. This is the default implementation of {@link FeatureStore}.
 */
public class InMemoryFeatureStore implements FeatureStore, DiagnosticDescription {
  private static final Logger logger = LoggerFactory.getLogger(InMemoryFeatureStore.class);

  private volatile ImmutableMap<VersionedDataKind<?>, Map<String, VersionedData>> allData = ImmutableMap.of();
  private volatile boolean initialized = false;
  private Object writeLock = new Object();

  @Override
  public <T extends VersionedData> T get(VersionedDataKind<T> kind, String key) {
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
  }

  @Override
  public <T extends VersionedData> Map<String, T> all(VersionedDataKind<T> kind) {
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
  }

  @Override
  public void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
    synchronized (writeLock) {
      ImmutableMap.Builder<VersionedDataKind<?>, Map<String, VersionedData>> newData = ImmutableMap.builder();
      for (Map.Entry<VersionedDataKind<?>, Map<String, ? extends VersionedData>> entry: allData.entrySet()) {
        // Note, the FeatureStore contract specifies that we should clone all of the maps. This doesn't
        // really make a difference in regular use of the SDK, but not doing it could cause unexpected
        // behavior in tests.
        newData.put(entry.getKey(), ImmutableMap.copyOf(entry.getValue()));
      }
      this.allData = newData.build(); // replaces the entire map atomically
      this.initialized = true;
    }
  }

  @Override
  public <T extends VersionedData> void delete(VersionedDataKind<T> kind, String key, int version) {
    upsert(kind, kind.makeDeletedItem(key, version));
  }

  @Override
  public <T extends VersionedData> void upsert(VersionedDataKind<T> kind, T item) {
    String key = item.getKey();
    synchronized (writeLock) {
      Map<String, VersionedData> existingItems = this.allData.get(kind);
      VersionedData oldItem = null;
      if (existingItems != null) {
        oldItem = existingItems.get(key);
        if (oldItem.getVersion() >= item.getVersion()) {
          return;
        }
      }
      // The following logic is necessary because ImmutableMap.Builder doesn't support overwriting an existing key
      ImmutableMap.Builder<VersionedDataKind<?>, Map<String, VersionedData>> newData = ImmutableMap.builder();
      for (Map.Entry<VersionedDataKind<?>, Map<String, VersionedData>> e: this.allData.entrySet()) {
        if (!e.getKey().equals(kind)) {
          newData.put(e.getKey(), e.getValue());
        }
      }
      if (existingItems == null) {
        newData.put(kind, ImmutableMap.<String, VersionedData>of(key, item));
      } else {
        ImmutableMap.Builder<String, VersionedData> itemsBuilder = ImmutableMap.builder();
        if (oldItem == null) {
          itemsBuilder.putAll(existingItems);
        } else {
          for (Map.Entry<String, VersionedData> e: existingItems.entrySet()) {
            if (!e.getKey().equals(key)) {
              itemsBuilder.put(e.getKey(), e.getValue());
            }
          }
        }
        itemsBuilder.put(key, item);
        newData.put(kind, itemsBuilder.build());
      }
      this.allData = newData.build(); // replaces the entire map atomically
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

  @Override
  public LDValue describeConfiguration(LDConfig config) {
    return LDValue.of("memory");
  }
}
