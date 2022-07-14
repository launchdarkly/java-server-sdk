package com.launchdarkly.sdk.server.subsystems;

import com.google.common.collect.ImmutableList;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Types that are used by the {@link DataStore} interface.
 * <p>
 * Applications should never need to use any of these types unless they are implementing a custom
 * data store.
 * 
 * @since 5.0.0
 */
public abstract class DataStoreTypes {
  private DataStoreTypes() {}
  
  /**
   * Represents a separately namespaced collection of storable data items.
   * <p>
   * The SDK passes instances of this type to the data store to specify whether it is referring to
   * a feature flag, a user segment, etc. The data store implementation should not look for a
   * specific data kind (such as feature flags), but should treat all data kinds generically.
   */
  public static final class DataKind {
    private final String name;
    private final Function<ItemDescriptor, String> serializer;
    private final Function<String, ItemDescriptor> deserializer;
    
    /**
     * A case-sensitive alphabetic string that uniquely identifies this data kind.
     * <p>
     * This is in effect a namespace for a collection of items of the same kind. Item keys must be
     * unique within that namespace. Persistent data store implementations could use this string
     * as part of a composite key or table name.
     * 
     * @return the namespace string
     */
    public String getName() {
      return name;
    }
    
    /**
     * Returns a serialized representation of an item of this kind.
     * <p>
     * The SDK uses this function to generate the data that is stored by a {@link PersistentDataStore}.
     * Store implementations normally do not need to call it, except in a special case described in the
     * documentation for {@link PersistentDataStore} regarding deleted item placeholders.
     * 
     * @param item an {@link ItemDescriptor} describing the object to be serialized
     * @return the serialized representation
     * @exception ClassCastException if the object is of the wrong class
     */
    public String serialize(ItemDescriptor item) {
      return serializer.apply(item);
    }
    
    /**
     * Creates an item of this kind from its serialized representation.
     * <p>
     * The SDK uses this function to translate data that is returned by a {@link PersistentDataStore}.
     * Store implementations do not normally need to call it, but there is a special case described in
     * the documentation for {@link PersistentDataStore}, regarding updates.
     * <p>
     * The returned {@link ItemDescriptor} has two properties: {@link ItemDescriptor#getItem()}, which
     * is the deserialized object <i>or</i> a {@code null} value for a deleted item placeholder, and
     * {@link ItemDescriptor#getVersion()}, which provides the object's version number regardless of
     * whether it is deleted or not.
     * 
     * @param s the serialized representation
     * @return an {@link ItemDescriptor} describing the deserialized object
     */
    public ItemDescriptor deserialize(String s) {
      return deserializer.apply(s);
    }
    
    /**
     * Constructs a DataKind instance.
     * 
     * @param name the value for {@link #getName()}
     * @param serializer the function to use for {@link #serialize(DataStoreTypes.ItemDescriptor)}
     * @param deserializer the function to use for {@link #deserialize(String)}
     */
    public DataKind(String name, Function<ItemDescriptor, String> serializer, Function<String, ItemDescriptor> deserializer) {
      this.name = name;
      this.serializer = serializer;
      this.deserializer = deserializer;
    }
    
    @Override
    public String toString() {
      return "DataKind(" + name + ")";
    }
  }

  /**
   * A versioned item (or placeholder) storable in a {@link DataStore}.
   * <p>
   * This is used for data stores that directly store objects as-is, as the default in-memory
   * store does. Items are typed as {@code Object}; the store should not know or care what the
   * actual object is.
   * <p>
   * For any given key within a {@link DataKind}, there can be either an existing item with a
   * version, or a "tombstone" placeholder representing a deleted item (also with a version).
   * Deleted item placeholders are used so that if an item is first updated with version N and
   * then deleted with version N+1, but the SDK receives those changes out of order, version N
   * will not overwrite the deletion.
   * <p>
   * Persistent data stores use {@link SerializedItemDescriptor} instead.
   */
  public static final class ItemDescriptor {
    private final int version;
    private final Object item;
    
    /**
     * Returns the version number of this data, provided by the SDK.
     * 
     * @return the version number
     */
    public int getVersion() {
      return version;
    }
    
    /**
     * Returns the data item, or null if this is a placeholder for a deleted item.
     * 
     * @return an object or null
     */
    public Object getItem() {
      return item;
    }
    
    /**
     * Constructs a new instance.
     * 
     * @param version the version number
     * @param item an object or null
     */
    public ItemDescriptor(int version, Object item) {
      this.version = version;
      this.item = item;
    }
    
    /**
     * Convenience method for constructing a deleted item placeholder.
     * 
     * @param version the version number
     * @return an ItemDescriptor
     */
    public static ItemDescriptor deletedItem(int version) {
      return new ItemDescriptor(version, null);
    }
    
    @Override
    public boolean equals(Object o) {
      if (o instanceof ItemDescriptor) {
        ItemDescriptor other = (ItemDescriptor)o;
        return version == other.version && Objects.equals(item, other.item);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return Objects.hash(version, item);
    }
    
    @Override
    public String toString() {
      return "ItemDescriptor(" + version + "," + item + ")";
    }
  }
  
  /**
   * A versioned item (or placeholder) storable in a {@link PersistentDataStore}.
   * <p>
   * This is equivalent to {@link ItemDescriptor}, but is used for persistent data stores. The
   * SDK will convert each data item to and from its serialized string form; the persistent data
   * store deals only with the serialized form.
   */
  public static final class SerializedItemDescriptor {
    private final int version;
    private final boolean deleted;
    private final String serializedItem;

    /**
     * Returns the version number of this data, provided by the SDK.
     * @return the version number
     */
    public int getVersion() {
      return version;
    }

    /**
     * Returns true if this is a placeholder (tombstone) for a deleted item. If so,
     * {@link #getSerializedItem()} will still contain a string representing the deleted item, but
     * the persistent store implementation has the option of not storing it if it can represent the
     * placeholder in a more efficient way.
     * 
     * @return true if this is a deleted item placeholder
     */
    public boolean isDeleted() {
      return deleted;
    }
    
    /**
     * Returns the data item's serialized representation. This will never be null; for a deleted item
     * placeholder, it will contain a special value that can be stored if necessary (see {@link #isDeleted()}).
     * 
     * @return the serialized data or null
     */
    public String getSerializedItem() {
      return serializedItem;
    }

    /**
     * Constructs a new instance.
     * 
     * @param version the version number
     * @param deleted true if this is a deleted item placeholder
     * @param serializedItem the serialized data (will not be null)
     */
    public SerializedItemDescriptor(int version, boolean deleted, String serializedItem) {
      this.version = version;
      this.deleted = deleted;
      this.serializedItem = serializedItem;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof SerializedItemDescriptor) {
        SerializedItemDescriptor other = (SerializedItemDescriptor)o;
        return version == other.version && deleted == other.deleted &&
            Objects.equals(serializedItem, other.serializedItem);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return Objects.hash(version, deleted, serializedItem);
    }
    
    @Override
    public String toString() {
      return "SerializedItemDescriptor(" + version + "," + deleted + "," + serializedItem + ")";
    }
  }
  
  /**
   * Wrapper for a set of storable items being passed to a data store.
   * <p>
   * Since the generic type signature for the data set is somewhat complicated (it is an ordered
   * list of key-value pairs where each key is a {@link DataKind}, and each value is another ordered
   * list of key-value pairs for the individual data items), this type simplifies the declaration of
   * data store methods and makes it easier to see what the type represents.
   * 
   * @param <TDescriptor> will be {@link ItemDescriptor} or {@link SerializedItemDescriptor}
   */
  public static final class FullDataSet<TDescriptor> {
    private final Iterable<Map.Entry<DataKind, KeyedItems<TDescriptor>>> data;
    
    /**
     * Returns the wrapped data set.
     * 
     * @return an enumeration of key-value pairs; may be empty, but will not be null
     */
    public Iterable<Map.Entry<DataKind, KeyedItems<TDescriptor>>> getData() {
      return data;
    }
    
    /**
     * Constructs a new instance.
     * 
     * @param data the data set
     */
    public FullDataSet(Iterable<Map.Entry<DataKind, KeyedItems<TDescriptor>>> data) {
      this.data = data == null ? ImmutableList.of(): data;
    }
    
    @Override
    public boolean equals(Object o) {
      return o instanceof FullDataSet<?> && data.equals(((FullDataSet<?>)o).data);
    }
    
    @Override
    public int hashCode() {
      return data.hashCode();
    }
  }
  
  /**
   * Wrapper for a set of storable items being passed to a data store, within a single
   * {@link DataKind}.
   *
   * @param <TDescriptor> will be {@link ItemDescriptor} or {@link SerializedItemDescriptor}
   */
  public static final class KeyedItems<TDescriptor> {
    private final Iterable<Map.Entry<String, TDescriptor>> items;
    
    /**
     * Returns the wrapped data set.
     * 
     * @return an enumeration of key-value pairs; may be empty, but will not be null
     */
    public Iterable<Map.Entry<String, TDescriptor>> getItems() {
      return items;
    }
    
    /**
     * Constructs a new instance.
     * 
     * @param items the data set
     */
    public KeyedItems(Iterable<Map.Entry<String, TDescriptor>> items) {
      this.items = items == null ? ImmutableList.of() : items; 
    }
    
    @Override
    public boolean equals(Object o) {
      return o instanceof KeyedItems<?> && items.equals(((KeyedItems<?>)o).items);
    }
    
    @Override
    public int hashCode() {
      return items.hashCode();
    }
  }
}
