package com.launchdarkly.client.utils;

import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.VersionedData;
import com.launchdarkly.client.VersionedDataKind;

import java.io.Closeable;
import java.util.Map;

/**
 * FeatureStoreCore is an interface for a simplified subset of the functionality of
 * {@link FeatureStore}, to be used in conjunction with {@link CachingStoreWrapper}. This allows
 * developers of custom FeatureStore implementations to avoid repeating logic that would
 * commonly be needed in any such implementation, such as caching. Instead, they can implement
 * only FeatureStoreCore and then create a CachingStoreWrapper.
 * <p>
 * Note that these methods do not take any generic type parameters; all storeable entities are
 * treated as implementations of the {@link VersionedData} interface, and a {@link VersionedDataKind}
 * instance is used to specify what kind of entity is being referenced. If entities will be
 * marshaled and unmarshaled, this must be done by reflection, using the type specified by
 * {@link VersionedDataKind#getItemClass()}; the methods in {@link FeatureStoreHelpers} may be
 * useful for this.
 * 
 * @since 4.6.0
 */
public interface FeatureStoreCore extends Closeable {
  /**
   * Returns the object to which the specified key is mapped, or null if no such item exists.
   * The method should not attempt to filter out any items based on their isDeleted() property,
   * nor to cache any items.
   *
   * @param kind the kind of object to get
   * @param key the key whose associated object is to be returned
   * @return the object to which the specified key is mapped, or null
   */
  VersionedData getInternal(VersionedDataKind<?> kind, String key);

  /**
   * Returns a {@link java.util.Map} of all associated objects of a given kind. The method
   * should not attempt to filter out any items based on their isDeleted() property, nor to
   * cache any items.
   *
   * @param kind the kind of objects to get
   * @return a map of all associated objects.
   */
  Map<String, VersionedData> getAllInternal(VersionedDataKind<?> kind);

  /**
   * Initializes (or re-initializes) the store with the specified set of objects. Any existing entries
   * will be removed. Implementations can assume that this set of objects is up to date-- there is no
   * need to perform individual version comparisons between the existing objects and the supplied
   * data.
   * <p>
   * If possible, the store should update the entire data set atomically. If that is not possible, it
   * should iterate through the outer map and then the inner map <i>in the order provided</> (the SDK
   * will use a Map subclass that has a defined ordering), storing each item, and then delete any
   * leftover items at the very end.
   *
   * @param allData all objects to be stored
   */
  void initInternal(Map<VersionedDataKind<?>, Map<String, VersionedData>> allData);

  /**
   * Updates or inserts the object associated with the specified key. If an item with the same key
   * already exists, it should update it only if the new item's getVersion() value is greater than
   * the old one. It should return the final state of the item, i.e. if the update succeeded then
   * it returns the item that was passed in, and if the update failed due to the version check
   * then it returns the item that is currently in the data store (this ensures that
   * CachingStoreWrapper will update the cache correctly).
   *
   * @param kind the kind of object to update
   * @param item the object to update or insert
   * @return the state of the object after the update
   */
  VersionedData upsertInternal(VersionedDataKind<?> kind, VersionedData item);

  /**
   * Returns true if this store has been initialized. In a shared data store, it should be able to
   * detect this even if initInternal was called in a different process, i.e. the test should be
   * based on looking at what is in the data store. The method does not need to worry about caching
   * this value; CachingStoreWrapper will only call it when necessary.
   *
   * @return true if this store has been initialized
   */
  boolean initializedInternal();
}
