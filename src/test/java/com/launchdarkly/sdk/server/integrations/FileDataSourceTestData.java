package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.LDValue;

import java.io.IOException;
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
  public static final LDValue FULL_SEGMENT_1 = LDValue.parse("{\"key\":\"seg1\",\"include\":[\"user1\"]}");
  public static final Map<String, LDValue> FULL_SEGMENTS =
      ImmutableMap.<String, LDValue>of(FULL_SEGMENT_1_KEY, FULL_SEGMENT_1);
  
  public static final Set<String> ALL_FLAG_KEYS = ImmutableSet.of(FULL_FLAG_1_KEY, FLAG_VALUE_1_KEY);
  public static final Set<String> ALL_SEGMENT_KEYS = ImmutableSet.of(FULL_SEGMENT_1_KEY);
  
  public static Path resourceFilePath(String filename) throws URISyntaxException {
    URL resource = FileDataSourceTestData.class.getClassLoader().getResource("filesource/" + filename);
    return Paths.get(resource.toURI());
  }
  
  public static String getResourceContents(String filename) throws Exception {
    return new String(Files.readAllBytes(resourceFilePath(filename)));
  }

  // These helpers ensure that we clean up all temporary files, and also that we only create temporary
  // files within our own temporary directories - since creating a file within a shared system temp
  // directory might mean there are thousands of other files there, which could be a problem if the
  // filesystem watcher implementation has to traverse the directory.
  
  static class TempDir implements AutoCloseable {
    final Path path;
    
    private TempDir(Path path) {
      this.path = path;
    }
    
    public void close() throws IOException {
      Files.delete(path);
    }
    
    public static TempDir create() throws IOException {
      return new TempDir(Files.createTempDirectory("java-sdk-tests"));
    }
    
    public TempFile tempFile(String suffix) throws IOException {
      return new TempFile(Files.createTempFile(path, "java-sdk-tests", suffix));
    }
  }
  
  // These helpers ensure that we clean up all temporary files, and also that we only create temporary
  // files within our own temporary directories - since creating a file within a shared system temp
  // directory might mean there are thousands of other files there, which could be a problem if the
  // filesystem watcher implementation has to traverse the directory.
  
  static class TempFile implements AutoCloseable {
    final Path path;
    
    private TempFile(Path path) {
      this.path = path;
    }
  
    @Override
    public void close() throws IOException {
      delete();
    }
    
    public void delete() throws IOException {
      Files.delete(path);
    }
    
    public void setContents(String content) throws IOException {
      Files.write(path, content.getBytes("UTF-8"));
    }
  }
}
