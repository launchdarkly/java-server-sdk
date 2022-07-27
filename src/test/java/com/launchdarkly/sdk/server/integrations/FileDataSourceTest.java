package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.TestComponents;
import com.launchdarkly.sdk.server.TestComponents.MockDataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataStore;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.common.collect.Iterables.size;
import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toItemsMap;
import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestComponents.inMemoryDataStore;
import static com.launchdarkly.sdk.server.TestUtil.requireDataSourceStatus;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.ALL_FLAG_KEYS;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.ALL_SEGMENT_KEYS;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.resourceFilePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class FileDataSourceTest {
  private static final Path badFilePath = Paths.get("no-such-file.json");
  
  private final DataStore store;
  private MockDataSourceUpdates dataSourceUpdates;
  private final LDConfig config = new LDConfig.Builder().build();
  
  public FileDataSourceTest() throws Exception {
    store = inMemoryDataStore();
    dataSourceUpdates = TestComponents.dataSourceUpdates(store);
  }
  
  private static FileDataSourceBuilder makeFactoryWithFile(Path path) {
    return FileData.dataSource().filePaths(path);
  }

  private DataSource makeDataSource(FileDataSourceBuilder builder) {
    return builder.build(clientContext("", config, dataSourceUpdates));
  }
  
  @Test
  public void flagsAreNotLoadedUntilStart() throws Exception {
    FileDataSourceBuilder factory = makeFactoryWithFile(resourceFilePath("all-properties.json"));
    try (DataSource fp = makeDataSource(factory)) {
      assertThat(store.isInitialized(), equalTo(false));
      assertThat(size(store.getAll(FEATURES).getItems()), equalTo(0));
      assertThat(size(store.getAll(SEGMENTS).getItems()), equalTo(0));
    }
  }
  
  @Test
  public void flagsAreLoadedOnStart() throws Exception {
    FileDataSourceBuilder factory = makeFactoryWithFile(resourceFilePath("all-properties.json"));
    try (DataSource fp = makeDataSource(factory)) {
      verifySuccessfulStart(fp);
      
      assertThat(toItemsMap(store.getAll(FEATURES)).keySet(), equalTo(ALL_FLAG_KEYS));
      assertThat(toItemsMap(store.getAll(SEGMENTS)).keySet(), equalTo(ALL_SEGMENT_KEYS));
    }
  }

  @Test
  public void filePathsCanBeSpecifiedAsStrings() throws Exception {
    FileDataSourceBuilder factory = FileData.dataSource().filePaths(resourceFilePath("all-properties.json").toString());
    try (DataSource fp = makeDataSource(factory)) {
      verifySuccessfulStart(fp);
      
      assertThat(toItemsMap(store.getAll(FEATURES)).keySet(), equalTo(ALL_FLAG_KEYS));
      assertThat(toItemsMap(store.getAll(SEGMENTS)).keySet(), equalTo(ALL_SEGMENT_KEYS));
    }
  }

  @Test
  public void flagsAreLoadedOnStartFromYamlFile() throws Exception {
    FileDataSourceBuilder factory = makeFactoryWithFile(resourceFilePath("all-properties.yml"));
    try (DataSource fp = makeDataSource(factory)) {
      verifySuccessfulStart(fp);
      
      assertThat(toItemsMap(store.getAll(FEATURES)).keySet(), equalTo(ALL_FLAG_KEYS));
      assertThat(toItemsMap(store.getAll(SEGMENTS)).keySet(), equalTo(ALL_SEGMENT_KEYS));
    }
  }

  @Test
  public void startSucceedsWithEmptyFile() throws Exception {
    try (DataSource fp = makeDataSource(makeFactoryWithFile(resourceFilePath("no-data.json")))) {
      verifySuccessfulStart(fp);
      
      assertThat(toItemsMap(store.getAll(FEATURES)).keySet(), equalTo(ImmutableSet.of()));
      assertThat(toItemsMap(store.getAll(SEGMENTS)).keySet(), equalTo(ImmutableSet.of()));
    }
  }
  
  private void verifySuccessfulStart(DataSource fp) {
    BlockingQueue<DataSourceStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.register(statuses::add);
    
    Future<Void> future = fp.start();
    
    assertThat(future.isDone(), equalTo(true));
    assertThat(store.isInitialized(), equalTo(true));
    requireDataSourceStatus(statuses, DataSourceStatusProvider.State.VALID);
  }

  @Test
  public void startFailsWithNonexistentFile() throws Exception {
    try (DataSource fp = makeDataSource(makeFactoryWithFile(badFilePath))) {
      verifyUnsuccessfulStart(fp);
    }
  }

  @Test
  public void startFailsWithNonexistentClasspathResource() throws Exception {
    FileDataSourceBuilder factory = FileData.dataSource().classpathResources("we-have-no-such-thing");
    try (DataSource fp = makeDataSource(factory)) {
      verifyUnsuccessfulStart(fp);
    }
  }

  private void verifyUnsuccessfulStart(DataSource fp) {
    BlockingQueue<DataSourceStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.register(statuses::add);
    
    Future<Void> future = fp.start();
    
    assertThat(future.isDone(), equalTo(true));
    assertThat(store.isInitialized(), equalTo(false));
    DataSourceStatusProvider.Status status = requireDataSourceStatus(statuses, DataSourceStatusProvider.State.INITIALIZING);
    assertEquals(DataSourceStatusProvider.ErrorKind.INVALID_DATA, status.getLastError().getKind());
  }
}
