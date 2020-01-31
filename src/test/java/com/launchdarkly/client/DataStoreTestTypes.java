package com.launchdarkly.client;

import com.google.common.base.Objects;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;

@SuppressWarnings("javadoc")
public class DataStoreTestTypes {
  public static class TestItem implements VersionedData {
    public final String name;
    public final String key;
    public final int version;
    public final boolean deleted;
    
    public TestItem(String name, String key, int version) {
      this(name, key, version, false);
    }

    public TestItem(String name, String key, int version, boolean deleted) {
      this.name = name;
      this.key = key;
      this.version = version;
      this.deleted = deleted;
    }

    @Override
    public String getKey() {
      return key;
    }

    @Override
    public int getVersion() {
      return version;
    }

    @Override
    public boolean isDeleted() {
      return deleted;
    }
    
    public TestItem withName(String newName) {
      return new TestItem(newName, key, version, deleted);
    }
    
    public TestItem withVersion(int newVersion) {
      return new TestItem(name, key, newVersion, deleted);
    }
    
    public TestItem withDeleted(boolean newDeleted) {
      return new TestItem(name, key, version, newDeleted);
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof TestItem) {
        TestItem o = (TestItem)other;
        return Objects.equal(name, o.name) &&
            Objects.equal(key, o.key) &&
            version == o.version &&
            deleted == o.deleted;
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return Objects.hashCode(name, key, version, deleted);
    }
    
    @Override
    public String toString() {
      return "TestItem(" + name + "," + key + "," + version + "," + deleted + ")";
    }
  }
  
  public static final VersionedDataKind<TestItem> TEST_ITEMS = new VersionedDataKind<DataStoreTestTypes.TestItem>() {
    @Override
    public String getNamespace() {
      return "test-items";
    }

    @Override
    public Class<TestItem> getItemClass() {
      return TestItem.class;
    }

    @Override
    public String getStreamApiPath() {
      return null;
    }

    @Override
    public TestItem makeDeletedItem(String key, int version) {
      return new TestItem(null, key, version, true);
    }

    @Override
    public TestItem deserialize(String serializedData) {
      return JsonHelpers.gsonInstance().fromJson(serializedData, TestItem.class);
    }
  };

  public static final VersionedDataKind<TestItem> OTHER_TEST_ITEMS = new VersionedDataKind<DataStoreTestTypes.TestItem>() {
    @Override
    public String getNamespace() {
      return "other-test-items";
    }

    @Override
    public Class<TestItem> getItemClass() {
      return TestItem.class;
    }

    @Override
    public String getStreamApiPath() {
      return null;
    }

    @Override
    public TestItem makeDeletedItem(String key, int version) {
      return new TestItem(null, key, version, true);
    }

    @Override
    public TestItem deserialize(String serializedData) {
      return JsonHelpers.gsonInstance().fromJson(serializedData, TestItem.class);
    }
  };
}
