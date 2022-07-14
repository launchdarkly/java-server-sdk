package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.SerializedItemDescriptor;

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
 * Conceptually, each item in the store is a {@link SerializedItemDescriptor} which always has
 * a version number, and can represent either a serialized object or a placeholder (tombstone)
 * for a deleted item. There are two approaches a persistent store implementation can use for
 * persisting this data:
 * <p>
 * 1. Preferably, it should store the version number and the {@link SerializedItemDescriptor#isDeleted()}
 * state separately so that the object does not need to be fully deserialized to read them. In
 * this case, deleted item placeholders can ignore the value of {@link SerializedItemDescriptor#getSerializedItem()}
 * on writes and can set it to null on reads. The store should never call {@link DataKind#deserialize(String)}
 * or {@link DataKind#serialize(DataStoreTypes.ItemDescriptor)}.
 * <p>
 * 2. If that isn't possible, then the store should simply persist the exact string from
 * {@link SerializedItemDescriptor#getSerializedItem()} on writes, and return the persisted
 * string on reads (returning zero for the version and false for {@link SerializedItemDescriptor#isDeleted()}).
 * The string is guaranteed to provide the SDK with enough information to infer the version and
 * the deleted state. On updates, the store must call {@link DataKind#deserialize(String)} in
 * order to inspect the version number of the existing item if any.
 * <p>
 * Error handling is defined as follows: if any data store operation encounters a database error, or
 * is otherwise unable to complete its task, it should throw a {@code RuntimeException} to make the SDK
 * aware of this. The SDK will log the exception and will assume that the data store is now in a
 * non-operational state; the SDK will then start polling {@link #isStoreAvailable()} to determine
 * when the store has started working again.
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
   * If the key is not known at all, the method should return null. Otherwise, it should return
   * a {@link SerializedItemDescriptor} as follows:
   * <p>
   * 1. If the version number and deletion state can be determined without fully deserializing
   * the item, then the store should set those properties in the {@link SerializedItemDescriptor}
   * (and can set {@link SerializedItemDescriptor#getSerializedItem()} to null for deleted items).
   * <p>
   * 2. Otherwise, it should simply set {@link SerializedItemDescriptor#getSerializedItem()} to
   * the exact string that was persisted, and can leave the other properties as zero/false. See
   * comments on {@link PersistentDataStore} for more about this.
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
   * If the store contains placeholders for deleted items, it should include them in the results,
   * not filter them out. See {@link #get(DataStoreTypes.DataKind, String)} for how to set the properties of the
   * {@link SerializedItemDescriptor} for each item. 
   * 
   * @param kind specifies which collection to use
   * @return a collection of key-value pairs; the ordering is not significant
   */
  KeyedItems<SerializedItemDescriptor> getAll(DataKind kind);
  
  /**
   * Updates or inserts an item in the specified collection.
   * <p>
   * If the given key already exists in that collection, the store must check the version number
   * of the existing item (even if it is a deleted item placeholder); if that version is greater
   * than or equal to the version of the new item, the update fails and the method returns false.
   * If the store is not able to determine the version number of an existing item without fully
   * deserializing the existing item, then it is allowed to call {@link DataKind#deserialize(String)}
   * for that purpose.
   * <p>
   * If the item's {@link SerializedItemDescriptor#isDeleted()} method returns true, this is a
   * deleted item placeholder. The store must persist this, rather than simply removing the key
   * from the store. The SDK will provide a string in {@link SerializedItemDescriptor#getSerializedItem()}
   * which the store can persist for this purpose; or, if the store is capable of persisting the
   * version number and deleted state without storing anything else, it should do so.
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
  
  /**
   * Tests whether the data store seems to be functioning normally.
   * <p>
   * This should not be a detailed test of different kinds of operations, but just the smallest possible
   * operation to determine whether (for instance) we can reach the database.
   * <p>
   * Whenever one of the store's other methods throws an exception, the SDK will assume that it may have
   * become unavailable (e.g. the database connection was lost). The SDK will then call
   * {@link #isStoreAvailable()} at intervals until it returns true.
   * 
   * @return true if the underlying data store is reachable
   */
  public boolean isStoreAvailable();
}
