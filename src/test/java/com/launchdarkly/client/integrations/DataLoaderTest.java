package com.launchdarkly.client.integrations;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.launchdarkly.client.VersionedData;
import com.launchdarkly.client.VersionedDataKind;
import com.launchdarkly.client.integrations.FileDataSourceImpl.DataBuilder;
import com.launchdarkly.client.integrations.FileDataSourceImpl.DataLoader;
import com.launchdarkly.client.integrations.FileDataSourceParsing.FileDataException;

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;
import static com.launchdarkly.client.integrations.FileDataSourceTestData.FLAG_VALUE_1_KEY;
import static com.launchdarkly.client.integrations.FileDataSourceTestData.resourceFilePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class DataLoaderTest {
  private static final Gson gson = new Gson();
  private DataBuilder builder = new DataBuilder();
  
  @Test
  public void yamlFileIsAutoDetected() throws Exception {
    DataLoader ds = new DataLoader(ImmutableList.of(resourceFilePath("flag-only.yml")));
    ds.load(builder);
    assertDataHasItemsOfKind(FEATURES);
  }
  
  @Test
  public void jsonFileIsAutoDetected() throws Exception {
    DataLoader ds = new DataLoader(ImmutableList.of(resourceFilePath("segment-only.json")));
    ds.load(builder);
    assertDataHasItemsOfKind(SEGMENTS);
  }
  
  @Test
  public void canLoadMultipleFiles() throws Exception {
    DataLoader ds = new DataLoader(ImmutableList.of(resourceFilePath("flag-only.json"),
        resourceFilePath("segment-only.yml")));
    ds.load(builder);
    assertDataHasItemsOfKind(FEATURES);
    assertDataHasItemsOfKind(SEGMENTS);
  }
  
  @Test
  public void flagValueIsConvertedToFlag() throws Exception {
    DataLoader ds = new DataLoader(ImmutableList.of(resourceFilePath("value-only.json")));
    JsonObject expected = gson.fromJson(
        "{\"key\":\"flag2\",\"on\":true,\"fallthrough\":{\"variation\":0},\"variations\":[\"value2\"]," +
        "\"trackEvents\":false,\"deleted\":false,\"version\":0}",
        JsonObject.class);
    ds.load(builder);
    VersionedData flag = builder.build().get(FEATURES).get(FLAG_VALUE_1_KEY);
    JsonObject actual = gson.toJsonTree(flag).getAsJsonObject();
    // Note, we're comparing one property at a time here because the version of the Java SDK we're
    // building against may have more properties than it did when the test was written.
    for (Map.Entry<String, JsonElement> e: expected.entrySet()) {
      assertThat(actual.get(e.getKey()), equalTo(e.getValue()));
    }
  }
  
  @Test
  public void duplicateFlagKeyInFlagsThrowsException() throws Exception {
    try {
      DataLoader ds = new DataLoader(ImmutableList.of(resourceFilePath("flag-only.json"),
          resourceFilePath("flag-with-duplicate-key.json")));
      ds.load(builder);
    } catch (FileDataException e) {
      assertThat(e.getMessage(), containsString("key \"flag1\" was already defined"));
    }
  }

  @Test
  public void duplicateFlagKeyInFlagsAndFlagValuesThrowsException() throws Exception {
    try {
      DataLoader ds = new DataLoader(ImmutableList.of(resourceFilePath("flag-only.json"),
          resourceFilePath("value-with-duplicate-key.json")));
      ds.load(builder);
    } catch (FileDataException e) {
      assertThat(e.getMessage(), containsString("key \"flag1\" was already defined"));
    }
  }

  @Test
  public void duplicateSegmentKeyThrowsException() throws Exception {
    try {
      DataLoader ds = new DataLoader(ImmutableList.of(resourceFilePath("segment-only.json"),
          resourceFilePath("segment-with-duplicate-key.json")));
      ds.load(builder);
    } catch (FileDataException e) {
      assertThat(e.getMessage(), containsString("key \"seg1\" was already defined"));
    }
  }

  private void assertDataHasItemsOfKind(VersionedDataKind<?> kind) {
    Map<String, ? extends VersionedData> items = builder.build().get(kind);
    if (items == null || items.size() == 0) {
      Assert.fail("expected at least one item in \"" + kind.getNamespace() + "\", received: " + builder.build());
    }
  }
}
