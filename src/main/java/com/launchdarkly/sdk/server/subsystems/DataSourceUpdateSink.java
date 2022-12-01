package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;

/**
 * Interface that a data source implementation will use to push data into the SDK.
 * <p>
 * The data source interacts with this object, rather than manipulating the data store directly, so
 * that the SDK can perform any other necessary operations that must happen when data is updated.
 * 
 * @since 5.0.0
 * @see ClientContext#getDataSourceUpdateSink()
 */
public interface DataSourceUpdateSink {
  /**
   * Completely overwrites the current contents of the data store with a set of items for each collection.
   * <p>
   * If the underlying data store throws an error during this operation, the SDK will catch it, log it,
   * and set the data source state to {@link State#INTERRUPTED} with an error of
   * {@link ErrorKind#STORE_ERROR}. It will not rethrow the error to the data
   * source, but will simply return {@code false} to indicate that the operation failed.
   * 
   * @param allData a list of {@link DataStoreTypes.DataKind} instances and their corresponding data sets
   * @return true if the update succeeded, false if it failed
   */
  boolean init(FullDataSet<ItemDescriptor> allData);

  /**
   * Updates or inserts an item in the specified collection. For updates, the object will only be
   * updated if the existing version is less than the new version.
   * <p>
   * To mark an item as deleted, pass an {@link ItemDescriptor} that contains a null, with a version
   * number (you may use {@link ItemDescriptor#deletedItem(int)}). Deletions must be versioned so that
   * they do not overwrite a later update in case updates are received out of order.
   * <p>
   * If the underlying data store throws an error during this operation, the SDK will catch it, log it,
   * and set the data source state to {@link State#INTERRUPTED} with an error of
   * {@link ErrorKind#STORE_ERROR}. It will not rethrow the error to the data
   * source, but will simply return {@code false} to indicate that the operation failed.
   * 
   * @param kind specifies which collection to use
   * @param key the unique key for the item within that collection
   * @param item the item to insert or update
   * @return true if the update succeeded, false if it failed
   */
  boolean upsert(DataKind kind, String key, ItemDescriptor item); 
  
  /**
   * Returns an object that provides status tracking for the data store, if applicable.
   * <p>
   * This may be useful if the data source needs to be aware of storage problems that might require it
   * to take some special action: for instance, if a database outage may have caused some data to be
   * lost and therefore the data should be re-requested from LaunchDarkly.
   * 
   * @return a {@link DataStoreStatusProvider}
   */
  DataStoreStatusProvider getDataStoreStatusProvider();
  
  /**
   * Informs the SDK of a change in the data source's status.
   * <p>
   * Data source implementations should use this method if they have any concept of being in a valid
   * state, a temporarily disconnected state, or a permanently stopped state.
   * <p>
   * If {@code newState} is different from the previous state, and/or {@code newError} is non-null, the
   * SDK will start returning the new status (adding a timestamp for the change) from
   * {@link DataSourceStatusProvider#getStatus()}, and will trigger status change events to any
   * registered listeners.
   * <p>
   * A special case is that if {@code newState} is {@link State#INTERRUPTED},
   * but the previous state was {@link State#INITIALIZING}, the state will remain
   * at {@link State#INITIALIZING} because {@link State#INTERRUPTED}
   * is only meaningful after a successful startup.
   *  
   * @param newState the data source state
   * @param newError information about a new error, if any
   * @see DataSourceStatusProvider
   */
  void updateStatus(State newState, ErrorInfo newError);
}
