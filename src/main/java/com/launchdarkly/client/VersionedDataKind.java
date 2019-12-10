package com.launchdarkly.client;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

import static com.google.common.collect.Iterables.transform;

/**
 * The descriptor for a specific kind of {@link VersionedData} objects that may exist in a {@link FeatureStore}.
 * You will not need to refer to this type unless you are directly manipulating a {@code FeatureStore}
 * or writing your own {@code FeatureStore} implementation. If you are implementing a custom store, for
 * maximum forward compatibility you should only refer to {@link VersionedData}, {@link VersionedDataKind},
 * and {@link VersionedDataKind#ALL}, and avoid any dependencies on specific type descriptor instances
 * or any specific fields of the types they describe.
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
  
  /**
   * Used internally to match data URLs in the streaming API.
   * @param path path from an API message
   * @return the parsed key if the path refers to an object of this kind, otherwise null 
   */
  String getKeyFromStreamApiPath(String path) {
    return path.startsWith(getStreamApiPath()) ? path.substring(getStreamApiPath().length()) : null;
  }
  
  static abstract class Impl<T extends VersionedData> extends VersionedDataKind<T> {
    private final String namespace;
    private final Class<T> itemClass;
    private final String streamApiPath;
    private final int priority;
    
    Impl(String namespace, Class<T> itemClass, String streamApiPath, int priority) {
      this.namespace = namespace;
      this.itemClass = itemClass;
      this.streamApiPath = streamApiPath;
      this.priority = priority;
    }
    
    public String getNamespace() {
      return namespace;
    }
    
    public Class<T> getItemClass() {
      return itemClass;
    }
    
    public String getStreamApiPath() {
      return streamApiPath;
    }
    
    public int getPriority() {
      return priority;
    }
  }
  
  /**
   * The {@link VersionedDataKind} instance that describes feature flag data.
   */
  public static VersionedDataKind<FlagModel.FeatureFlag> FEATURES = new Impl<FlagModel.FeatureFlag>("features", FlagModel.FeatureFlag.class, "/flags/", 1) {
    public FlagModel.FeatureFlag makeDeletedItem(String key, int version) {
      return new FlagModel.FeatureFlag(key, version, false, null, null, null, null, null, null, null, false, false, false, null, true);
    }
    
    public boolean isDependencyOrdered() {
      return true;
    }
    
    public Iterable<String> getDependencyKeys(VersionedData item) {
      FlagModel.FeatureFlag flag = (FlagModel.FeatureFlag)item;
      if (flag.getPrerequisites() == null || flag.getPrerequisites().isEmpty()) {
        return ImmutableList.of();
      }
      return transform(flag.getPrerequisites(), new Function<FlagModel.Prerequisite, String>() {
        public String apply(FlagModel.Prerequisite p) {
          return p.getKey();
        }
      });
    }
  };
  
  /**
   * The {@link VersionedDataKind} instance that describes user segment data.
   */
  public static VersionedDataKind<FlagModel.Segment> SEGMENTS = new Impl<FlagModel.Segment>("segments", FlagModel.Segment.class, "/segments/", 0) {
    
    public FlagModel.Segment makeDeletedItem(String key, int version) {
      return new FlagModel.Segment(key, null, null, null, null, version, true);
    }
  };
  
  /**
   * A list of all existing instances of {@link VersionedDataKind}.
   * @since 4.1.0
   */
  public static Iterable<VersionedDataKind<?>> ALL = ImmutableList.of(FEATURES, SEGMENTS);
}
