package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.TestComponents;
import com.launchdarkly.sdk.server.TestComponents.MockDataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStore;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.common.collect.Iterables.size;
import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toItemsMap;
import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestComponents.inMemoryDataStore;
import static com.launchdarkly.sdk.server.TestUtil.repeatWithTimeout;
import static com.launchdarkly.sdk.server.TestUtil.requireDataSourceStatus;
import static com.launchdarkly.sdk.server.TestUtil.requireDataSourceStatusEventually;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.ALL_FLAG_KEYS;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.ALL_SEGMENT_KEYS;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.getResourceContents;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.resourceFilePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings("javadoc")
public class FileDataSourceTest {
  private static final Path badFilePath = Paths.get("no-such-file.json");
  
  private final DataStore store;
  private MockDataSourceUpdates dataSourceUpdates;
  private final LDConfig config = new LDConfig.Builder().build();
  private final FileDataSourceBuilder factory;
  
  public FileDataSourceTest() throws Exception {
    store = inMemoryDataStore();
    dataSourceUpdates = TestComponents.dataSourceUpdates(store);
    factory = makeFactoryWithFile(resourceFilePath("all-properties.json"));
  }
  
  private static FileDataSourceBuilder makeFactoryWithFile(Path path) {
    return FileData.dataSource().filePaths(path);
  }

  private DataSource makeDataSource(FileDataSourceBuilder builder) {
    return builder.createDataSource(clientContext("", config), dataSourceUpdates);
  }
  
  @Test
  public void flagsAreNotLoadedUntilStart() throws Exception {
    try (DataSource fp = makeDataSource(factory)) {
      assertThat(store.isInitialized(), equalTo(false));
      assertThat(size(store.getAll(FEATURES).getItems()), equalTo(0));
      assertThat(size(store.getAll(SEGMENTS).getItems()), equalTo(0));
    }
  }
  
  @Test
  public void flagsAreLoadedOnStart() throws Exception {
    try (DataSource fp = makeDataSource(factory)) {
      fp.start();
      assertThat(store.isInitialized(), equalTo(true));
      assertThat(toItemsMap(store.getAll(FEATURES)).keySet(), equalTo(ALL_FLAG_KEYS));
      assertThat(toItemsMap(store.getAll(SEGMENTS)).keySet(), equalTo(ALL_SEGMENT_KEYS));
    }
  }
  
  @Test
  public void startFutureIsCompletedAfterSuccessfulLoad() throws Exception {
    try (DataSource fp = makeDataSource(factory)) {
      Future<Void> future = fp.start();
      assertThat(future.isDone(), equalTo(true));
    }
  }
  
  @Test
  public void initializedIsTrueAfterSuccessfulLoad() throws Exception {
    try (DataSource fp = makeDataSource(factory)) {
      fp.start();
      assertThat(fp.isInitialized(), equalTo(true));
    }
  }

  @Test
  public void statusIsValidAfterSuccessfulLoad() throws Exception {
    BlockingQueue<DataSourceStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.register(statuses::add);
    
    try (DataSource fp = makeDataSource(factory)) {
      fp.start();
      assertThat(fp.isInitialized(), equalTo(true));
      
      requireDataSourceStatus(statuses, DataSourceStatusProvider.State.VALID);
    }
  }

  @Test
  public void startFutureIsCompletedAfterUnsuccessfulLoad() throws Exception {
    factory.filePaths(badFilePath);
    try (DataSource fp = makeDataSource(factory)) {
      Future<Void> future = fp.start();
      assertThat(future.isDone(), equalTo(true));
    }
  }
  
  @Test
  public void initializedIsFalseAfterUnsuccessfulLoad() throws Exception {
    factory.filePaths(badFilePath);
    try (DataSource fp = makeDataSource(factory)) {
      fp.start();
      assertThat(fp.isInitialized(), equalTo(false));
    }
  }

  @Test
  public void statusIsInitializingAfterUnsuccessfulLoad() throws Exception {
    BlockingQueue<DataSourceStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.register(statuses::add);
    
    factory.filePaths(badFilePath);
    try (DataSource fp = makeDataSource(factory)) {
      fp.start();
      assertThat(fp.isInitialized(), equalTo(false));
      
      DataSourceStatusProvider.Status status = requireDataSourceStatus(statuses, DataSourceStatusProvider.State.INITIALIZING);
      assertNotNull(status.getLastError());
      assertEquals(DataSourceStatusProvider.ErrorKind.INVALID_DATA, status.getLastError().getKind());
    }
  }
  
  @Test
  public void modifiedFileIsNotReloadedIfAutoUpdateIsOff() throws Exception {
    File file = makeTempFlagFile();
    FileDataSourceBuilder factory1 = makeFactoryWithFile(file.toPath());
    try {
      setFileContents(file, getResourceContents("flag-only.json"));
      try (DataSource fp = makeDataSource(factory1)) {
        fp.start();
        setFileContents(file, getResourceContents("segment-only.json"));
        Thread.sleep(400);
        assertThat(toItemsMap(store.getAll(FEATURES)).size(), equalTo(1));
        assertThat(toItemsMap(store.getAll(SEGMENTS)).size(), equalTo(0));
      }
    } finally {
      file.delete();
    }
  }

  // Note that the auto-update tests may fail when run on a Mac, but succeed on Ubuntu. This is because on
  // MacOS there is no native implementation of WatchService, and the default implementation is known
  // to be extremely slow. See: https://stackoverflow.com/questions/9588737/is-java-7-watchservice-slow-for-anyone-else
  @Test
  public void modifiedFileIsReloadedIfAutoUpdateIsOn() throws Exception {
    File file = makeTempFlagFile();
    FileDataSourceBuilder factory1 = makeFactoryWithFile(file.toPath()).autoUpdate(true);
    try {
      setFileContents(file, getResourceContents("flag-only.json"));  // this file has 1 flag
      try (DataSource fp = makeDataSource(factory1)) {
        fp.start();
        Thread.sleep(1000);
        setFileContents(file, getResourceContents("all-properties.json"));  // this file has all the flags
        repeatWithTimeout(Duration.ofSeconds(10), Duration.ofMillis(500), () -> {
          if (toItemsMap(store.getAll(FEATURES)).size() == ALL_FLAG_KEYS.size()) {
            // success - return a non-null value to make repeatWithTimeout end
            return fp;
          }
          return null;
        });
      }
    } finally {
      file.delete();
    }
  }
  
  @Test
  public void ifFilesAreBadAtStartTimeAutoUpdateCanStillLoadGoodDataLater() throws Exception {
    BlockingQueue<DataSourceStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.register(statuses::add);
    
    File file = makeTempFlagFile();
    setFileContents(file, "not valid");
    FileDataSourceBuilder factory1 = makeFactoryWithFile(file.toPath()).autoUpdate(true);
    try {
      try (DataSource fp = makeDataSource(factory1)) {
        fp.start();
        Thread.sleep(1000);
        setFileContents(file, getResourceContents("flag-only.json"));  // this file has 1 flag
        repeatWithTimeout(Duration.ofSeconds(10), Duration.ofMillis(500), () -> {
          if (toItemsMap(store.getAll(FEATURES)).size() > 0) {
            // success - status is now VALID, after having first been INITIALIZING - can still see that an error occurred
            DataSourceStatusProvider.Status status = requireDataSourceStatusEventually(statuses,
                DataSourceStatusProvider.State.VALID, DataSourceStatusProvider.State.INITIALIZING);
            assertNotNull(status.getLastError());
            assertEquals(DataSourceStatusProvider.ErrorKind.INVALID_DATA, status.getLastError().getKind());

            return status;
          }
          return null;
        });
      }
    } finally {
      file.delete();
    }
  }
  
  private File makeTempFlagFile() throws Exception {
    return File.createTempFile("flags", ".json");
  }
  
  private void setFileContents(File file, String content) throws Exception {
    Files.write(file.toPath(), content.getBytes("UTF-8"));
  }
}
