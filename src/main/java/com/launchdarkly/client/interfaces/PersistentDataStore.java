package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.client.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.client.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.client.interfaces.DataStoreTypes.KeyedItems;
import com.launchdarkly.client.interfaces.DataStoreTypes.SerializedItemDescriptor;

import java.io.Closeable;

/**
 * Interface for a data store that holds feature flags and related data in a serialized form.
 * <p>
 * This interface should be used for database integrations, or any other data store
 * implementation that stores data in some external service. The SDK will take care of
 * converting between its own internal data model and a serialized string form; the data
 * store interacts only with the serialized form. The SDK will also provide its own caching
 * layer on top of the persistent data store; the data store implementation should not
 * provide caching, but simply do every query or update that the SDK tells it to do.
 * <p>
 * Implementations must be thread-safe.
 * <p>
 * Conceptually, each item in the store is a {@link SerializedItemDescriptor} consisting of a
 * version number plus either a string of serialized data or a null; the null represents a
 * placeholder (tombstone) indicating that the item was deleted.
 * <p>
 * Preferably, the store implementation should store the version number as a separate property
 * from the string, and store a null or empty string for deleted items, so that no
 * deserialization is required to simply determine the version (for updates) or the deleted
 * state.
 * <p>
 * However, due to how persistent stores were implemented in earlier SDK versions, for
 * interoperability it may be necessary for a store to use a somewhat different model in
 * which the version number and deleted state are encoded inside the serialized string. In
 * this case, to avoid unnecessary extra parsing, the store should work as follows:
 * <ul>
 * <li> When querying items, set the {@link SerializedItemDescriptor} to have a version
 * number of zero; the SDK will be able to determine the version number, and to filter out
 * any items that were actually deleted, after it deserializes the item. </li>
 * <li> When inserting or updating items, if the {@link SerializedItemDescriptor} contains
 * a null, pass its version number to {@link DataKind#serializeDeletedItemPlaceholder(int)}
 * and store the string that that method returns. </li>
 * <li> When updating items, if it's necessary to check the version number of an existing
 * item, pass its serialized string to {@link DataKind#deserialize(String)} and look at the
 * version number in the returned {@link ItemDescriptor}. </li>
 * </ul>
 * 
 * @since 5.0.0
 */
public interface PersistentDataStore extends Closeable {
  /**
   * Overwrites the store's contents with a set of items for each collection.
   * <p>
   * All previous data should be discarded, regardless of versioning.
   * <p>
   * The update should be done atomically. If it cannot be done atomically, then the store
   * must first add or update each item in the same order that they are given in the input
   * data, and then delete any previously stored items that were not in the input data.
   * 
   * @param allData a list of {@link DataStoreTypes.DataKind} instances and their corresponding data sets
   */
  void init(FullDataSet<SerializedItemDescriptor> allData);
  
  /**
   * Retrieves an item from the specified collection, if available.
   * <p>
   * If the item has been deleted and the store contains a placeholder, it should return a
   * {@link SerializedItemDescriptor} for that placeholder rather than returning null.
   * <p>
   * If it is possible for the data store to know the version number of the data item without
   * deserializing it, then it should return that number in the version property of the
   * {@link SerializedItemDescriptor}. If not, then it should just return zero for the version
   * and it will be parsed out later.
   * 
   * @param kind specifies which collection to use
   * @param key the unique key of the item within that collection
   * @return a versioned item that contains the stored data (or placeholder for deleted data);
   *   null if the key is unknown
   */
  SerializedItemDescriptor get(DataKind kind, String key);
  
  /**
   * Retrieves all items from the specified collection.
   * <p>
   * If the store contains placeholders for deleted items, it should include them in
   * the results, not filter them out.
   * 
   * @param kind specifies which collection to use
   * @return a collection of key-value pairs; the ordering is not significant
   */
  KeyedItems<SerializedItemDescriptor> getAll(DataKind kind);
  
  /**
   * Updates or inserts an item in the specified collection. For updates, the object will only be
   * updated if the existing version is less than the new version.
   * <p>
   * The SDK may pass an {@link ItemDescriptor} that contains a null, to represent a placeholder
   * for a deleted item. In that case, assuming the version is greater than any existing version of
   * that item, the store should retain that placeholder rather than simply not storing anything.
   * 
   * @param kind specifies which collection to use
   * @param key the unique key for the item within that collection
   * @param item the item to insert or update
   * @return true if the item was updated; false if it was not updated because the store contains
   *   an equal or greater version
   */
  boolean upsert(DataKind kind, String key, SerializedItemDescriptor item);
  
  /**
   * Returns true if this store has been initialized.
   * <p>
   * In a shared data store, the implementation should be able to detect this state even if
   * {@link #init} was called in a different process, i.e. it must query the underlying
   * data store in some way. The method does not need to worry about caching this value; the SDK
   * will call it rarely.
   *
   * @return true if the store has been initialized
   */
  boolean isInitialized();
}
