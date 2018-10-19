package com.launchdarkly.client.files;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

public class TestData {
  private static final Gson gson = new Gson();
  
  // These should match the data in our test files
  public static final String FULL_FLAG_1_KEY = "flag1";
  public static final JsonElement FULL_FLAG_1 = gson.fromJson("{\"key\":\"flag1\",\"on\":true}", JsonElement.class);
  public static final Map<String, JsonElement> FULL_FLAGS =
      ImmutableMap.<String, JsonElement>of(FULL_FLAG_1_KEY, FULL_FLAG_1);
  
  public static final String FLAG_VALUE_1_KEY = "flag2";
  public static final JsonElement FLAG_VALUE_1 = new JsonPrimitive("value2");
  public static final Map<String, JsonElement> FLAG_VALUES =
      ImmutableMap.<String, JsonElement>of(FLAG_VALUE_1_KEY, FLAG_VALUE_1);

  public static final String FULL_SEGMENT_1_KEY = "seg1"; 
  public static final JsonElement FULL_SEGMENT_1 = gson.fromJson("{\"key\":\"seg1\",\"include\":[\"user1\"]}", JsonElement.class);
  public static final Map<String, JsonElement> FULL_SEGMENTS =
      ImmutableMap.<String, JsonElement>of(FULL_SEGMENT_1_KEY, FULL_SEGMENT_1);
  
  public static final Set<String> ALL_FLAG_KEYS = ImmutableSet.of(FULL_FLAG_1_KEY, FLAG_VALUE_1_KEY);
  public static final Set<String> ALL_SEGMENT_KEYS = ImmutableSet.of(FULL_SEGMENT_1_KEY);
  
  public static Path resourceFilePath(String filename) throws URISyntaxException {
    URL resource = TestData.class.getClassLoader().getResource("filesource/" + filename);
    return Paths.get(resource.toURI());
  }
  
  public static String getResourceContents(String filename) throws Exception {
    return new String(Files.readAllBytes(resourceFilePath(filename)));
  }

}
