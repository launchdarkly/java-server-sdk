package com.launchdarkly.client.interfaces;

import java.util.Map;

/**
 * Interface that a data source implementation will use to push data into the underlying
 * data store.
 * <p>
 * This layer of indirection allows the SDK to perform any other necessary operations that must
 * happen when data is updated, by providing its own implementation of {@link DataStoreUpdates}.
 * 
 * @since 5.0.0
 */
public interface DataStoreUpdates {
  /**
   * Overwrites the store's contents with a set of items for each collection.
   * <p>
   * All previous data will be discarded, regardless of versioning.
   * 
   * @param allData all objects to be stored
   */
  void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData);

  /**
   * Update or insert the object associated with the specified key, if its version is less than or
   * equal the version specified in the argument object.
   * <p>
   * Deletions are implemented by upserting a deleted item placeholder.
   *
   * @param <T> class of the object to be updated
   * @param kind the kind of object to update
   * @param item the object to update or insert
   */
  <T extends VersionedData> void upsert(VersionedDataKind<T> kind, T item);
}
