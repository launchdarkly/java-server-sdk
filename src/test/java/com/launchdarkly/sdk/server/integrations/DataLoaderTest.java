package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.integrations.FileDataSourceImpl.DataBuilder;
import com.launchdarkly.sdk.server.integrations.FileDataSourceImpl.DataLoader;
import com.launchdarkly.sdk.server.integrations.FileDataSourceParsing.FileDataException;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;
import com.launchdarkly.testhelpers.JsonTestValue;

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toDataMap;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.FLAG_VALUE_1_KEY;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.resourceFilePath;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.resourceLocation;
import static com.launchdarkly.testhelpers.JsonAssertions.jsonIncludes;
import static com.launchdarkly.testhelpers.JsonTestValue.jsonOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class DataLoaderTest {
  private DataBuilder builder = new DataBuilder(FileData.DuplicateKeysHandling.FAIL);

  @Test
  public void canLoadFromFilePath() throws Exception {
    DataLoader ds = new DataLoader(FileData.dataSource().filePaths(resourceFilePath("flag-only.json")).sources);
    ds.load(builder);
    assertDataHasItemsOfKind(FEATURES);
  }

  @Test
  public void canLoadFromClasspath() throws Exception {
    DataLoader ds = new DataLoader(FileData.dataSource().classpathResources(resourceLocation("flag-only.json")).sources);
    ds.load(builder);
    assertDataHasItemsOfKind(FEATURES);
  }
  

  @Test
  public void yamlFileIsAutoDetected() throws Exception {
    DataLoader ds = new DataLoader(FileData.dataSource().filePaths(resourceFilePath("flag-only.yml")).sources);
    ds.load(builder);
    assertDataHasItemsOfKind(FEATURES);
  }
  
  @Test
  public void jsonFileIsAutoDetected() throws Exception {
    DataLoader ds = new DataLoader(FileData.dataSource().filePaths(resourceFilePath("segment-only.json")).sources);
    ds.load(builder);
    assertDataHasItemsOfKind(SEGMENTS);
  }
  
  @Test
  public void canLoadMultipleFiles() throws Exception {
    DataLoader ds = new DataLoader(FileData.dataSource().filePaths(
        resourceFilePath("flag-only.json"),
        resourceFilePath("segment-only.yml")
        ).sources);
    ds.load(builder);
    assertDataHasItemsOfKind(FEATURES);
    assertDataHasItemsOfKind(SEGMENTS);
  }
  
  @Test
  public void flagValueIsConvertedToFlag() throws Exception {
    DataLoader ds = new DataLoader(FileData.dataSource().filePaths(resourceFilePath("value-only.json")).sources);
    String expected =
        "{\"key\":\"flag2\",\"on\":true,\"fallthrough\":{\"variation\":0},\"variations\":[\"value2\"]," +
        "\"trackEvents\":false,\"deleted\":false,\"version\":1}";
    ds.load(builder);
    assertThat(getItemAsJson(builder, FEATURES, FLAG_VALUE_1_KEY), jsonIncludes(expected));
    // Note, we're using jsonIncludes instead of jsonEquals because the version of the Java
    // SDK we're building against may have more properties than it did when the test was written.
  }
  
  @Test
  public void duplicateFlagKeyInFlagsThrowsExceptionByDefault() throws Exception {
    try {
      DataLoader ds = new DataLoader(FileData.dataSource().filePaths(
          resourceFilePath("flag-only.json"),
          resourceFilePath("flag-with-duplicate-key.json")
          ).sources);
      ds.load(builder);
    } catch (FileDataException e) {
      assertThat(e.getMessage(), containsString("key \"flag1\" was already defined"));
    }
  }

  @Test
  public void duplicateFlagKeyInFlagsAndFlagValuesThrowsExceptionByDefault() throws Exception {
    try {
      DataLoader ds = new DataLoader(FileData.dataSource().filePaths(
          resourceFilePath("flag-only.json"),
          resourceFilePath("value-with-duplicate-key.json")
          ).sources);
      ds.load(builder);
    } catch (FileDataException e) {
      assertThat(e.getMessage(), containsString("key \"flag1\" was already defined"));
    }
  }

  @Test
  public void duplicateSegmentKeyThrowsExceptionByDefault() throws Exception {
    try {
      DataLoader ds = new DataLoader(FileData.dataSource().filePaths(
          resourceFilePath("segment-only.json"),
          resourceFilePath("segment-with-duplicate-key.json")
          ).sources);
      ds.load(builder);
    } catch (FileDataException e) {
      assertThat(e.getMessage(), containsString("key \"seg1\" was already defined"));
    }
  }

  @Test
  public void duplicateKeysCanBeAllowed() throws Exception {
    DataBuilder data1 = new DataBuilder(FileData.DuplicateKeysHandling.IGNORE);
    DataLoader loader1 = new DataLoader(FileData.dataSource().filePaths(
        resourceFilePath("flag-only.json"),
        resourceFilePath("flag-with-duplicate-key.json")
        ).sources);
    loader1.load(data1);
    assertThat(getItemAsJson(data1, FEATURES, "flag1"), jsonIncludes("{\"on\":true}")); // value from first file
    
    DataBuilder data2 = new DataBuilder(FileData.DuplicateKeysHandling.IGNORE);
    DataLoader loader2 = new DataLoader(FileData.dataSource().filePaths(
        resourceFilePath("value-with-duplicate-key.json"),
        resourceFilePath("flag-only.json")
        ).sources);
    loader2.load(data2);
    assertThat(getItemAsJson(data2, FEATURES, "flag2"),
        jsonIncludes("{\"variations\":[\"value2a\"]}")); // value from first file
    
    DataBuilder data3 = new DataBuilder(FileData.DuplicateKeysHandling.IGNORE);
    DataLoader loader3 = new DataLoader(FileData.dataSource().filePaths(
        resourceFilePath("segment-only.json"),
        resourceFilePath("segment-with-duplicate-key.json")
        ).sources);
    loader3.load(data3);
    assertThat(getItemAsJson(data3, SEGMENTS, "seg1"),
        jsonIncludes("{\"included\":[\"user1\"]}")); // value from first file
  }
  
  @Test
  public void versionsAreIncrementedForEachLoad() throws Exception {
    DataLoader ds = new DataLoader(FileData.dataSource().filePaths(
        resourceFilePath("flag-only.json"),
        resourceFilePath("segment-only.json"),
        resourceFilePath("value-only.json")
        ).sources);
    
    DataBuilder data1 = new DataBuilder(FileData.DuplicateKeysHandling.FAIL);
    ds.load(data1);
    assertVersionsMatch(data1.build(), 1);
    
    DataBuilder data2 = new DataBuilder(FileData.DuplicateKeysHandling.FAIL);
    ds.load(data2);
    assertVersionsMatch(data2.build(), 2);
  }
  
  private void assertDataHasItemsOfKind(DataKind kind) {
    Map<String, ItemDescriptor> items = toDataMap(builder.build()).get(kind);
    if (items == null || items.size() == 0) {
      Assert.fail("expected at least one item in \"" + kind.getName() + "\", received: " + builder.build());
    }
  }
  
  private void assertVersionsMatch(FullDataSet<ItemDescriptor> data, int expectedVersion) {
    for (Map.Entry<DataKind, KeyedItems<ItemDescriptor>> kv1: data.getData()) {
      DataKind kind = kv1.getKey();
      for (Map.Entry<String, ItemDescriptor> kv2: kv1.getValue().getItems()) {
        ItemDescriptor item = kv2.getValue();
        String jsonData = kind.serialize(item);
        assertThat("descriptor version of " + kv2.getKey(), item.getVersion(), equalTo(expectedVersion));
        assertThat("version in data model object of " + kv2.getKey(), LDValue.parse(jsonData).get("version"),
            equalTo(LDValue.of(expectedVersion)));
      }
    }
  }
  
  private JsonTestValue getItemAsJson(DataBuilder builder, DataKind kind, String key) {
    ItemDescriptor flag = toDataMap(builder.build()).get(kind).get(key);
    return jsonOf(kind.serialize(flag));
  }
}
