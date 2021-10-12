package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.LDValue;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("javadoc")
public class FileDataSourceTestData {
  // These should match the data in our test files
  public static final String FULL_FLAG_1_KEY = "flag1";
  public static final LDValue FULL_FLAG_1 =
      LDValue.parse("{\"key\":\"flag1\",\"on\":true,\"fallthrough\":{\"variation\":2},\"variations\":[\"fall\",\"off\",\"on\"]}");
  public static final LDValue FULL_FLAG_1_VALUE = LDValue.of("on");
  public static final Map<String, LDValue> FULL_FLAGS =
      ImmutableMap.<String, LDValue>of(FULL_FLAG_1_KEY, FULL_FLAG_1);
  
  public static final String FLAG_VALUE_1_KEY = "flag2";
  public static final LDValue FLAG_VALUE_1 = LDValue.of("value2");
  public static final Map<String, LDValue> FLAG_VALUES =
      ImmutableMap.<String, LDValue>of(FLAG_VALUE_1_KEY, FLAG_VALUE_1);

  public static final String FULL_SEGMENT_1_KEY = "seg1"; 
  public static final LDValue FULL_SEGMENT_1 = LDValue.parse("{\"key\":\"seg1\",\"included\":[\"user1\"]}");
  public static final Map<String, LDValue> FULL_SEGMENTS =
      ImmutableMap.<String, LDValue>of(FULL_SEGMENT_1_KEY, FULL_SEGMENT_1);
  
  public static final Set<String> ALL_FLAG_KEYS = ImmutableSet.of(FULL_FLAG_1_KEY, FLAG_VALUE_1_KEY);
  public static final Set<String> ALL_SEGMENT_KEYS = ImmutableSet.of(FULL_SEGMENT_1_KEY);
  
  public static Path resourceFilePath(String filename) throws URISyntaxException {
    URL resource = FileDataSourceTestData.class.getClassLoader().getResource(resourceLocation(filename));
    return Paths.get(resource.toURI());
  }

  public static String resourceLocation(String filename) throws URISyntaxException {
    return "filesource/" + filename;
  }
  
  public static String getResourceContents(String filename) throws Exception {
    return new String(Files.readAllBytes(resourceFilePath(filename)));
  }
}
