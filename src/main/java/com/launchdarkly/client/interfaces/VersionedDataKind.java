package com.launchdarkly.client.interfaces;

import com.google.common.collect.ImmutableList;

/**
 * The descriptor for a specific kind of {@link VersionedData} objects that may exist in a {@link DataStore}.
 * You will not need to refer to this type unless you are directly manipulating a {@link DataStore}
 * or writing your own {@link DataStore} implementation. If you are implementing a custom store, for
 * maximum forward compatibility you should only refer to {@link VersionedData} and {@link VersionedDataKind},
 * and avoid any dependencies on specific type descriptor instances or any specific fields of the types they describe.
 * @param <T> the item type
 * @since 3.0.0
 */
public abstract class VersionedDataKind<T extends VersionedData> {
  
  /**
   * A short string that serves as the unique name for the collection of these objects, e.g. "features".
   * @return a namespace string
   */
  public abstract String getNamespace();
  
  /**
   * The Java class for objects of this type.
   * @return a Java class
   */
  public abstract Class<T> getItemClass();
  
  /**
   * The path prefix for objects of this type in events received from the streaming API.
   * @return the URL path
   */
  public abstract String getStreamApiPath();
  
  /**
   * Return an instance of this type with the specified key and version, and deleted=true.
   * @param key the unique key
   * @param version the version number
   * @return a new instance
   */
  public abstract T makeDeletedItem(String key, int version);
  
  /**
   * Deserialize an instance of this type from its string representation (normally JSON).
   * @param serializedData the serialized data
   * @return the deserialized object
   */
  public abstract T deserialize(String serializedData);
  
  /**
   * Used internally to determine the order in which collections are updated. The default value is
   * arbitrary; the built-in data kinds override it for specific data model reasons.
   * 
   * @return a zero-based integer; collections with a lower priority are updated first
   * @since 4.7.0
   */
  public int getPriority() {
    return getNamespace().length() + 10;
  }
  
  /**
   * Returns true if the SDK needs to store items of this kind in an order that is based on
   * {@link #getDependencyKeys(VersionedData)}.
   * 
   * @return true if dependency ordering should be used
   * @since 4.7.0
   */
  public boolean isDependencyOrdered() {
    return false;
  }
  
  /**
   * Gets all keys of items that this one directly depends on, if this kind of item can have
   * dependencies. 
   * <p>
   * Note that this does not use the generic type T, because it is called from code that only knows
   * about VersionedData, so it will need to do a type cast. However, it can rely on the item being
   * of the correct class.
   *
   * @param item the item
   * @return keys of dependencies of the item
   * @since 4.7.0
   */
  public Iterable<String> getDependencyKeys(VersionedData item) {
    return ImmutableList.of();
  }
  
  @Override
  public String toString() {
    return "{" + getNamespace() + "}";
  }
}
