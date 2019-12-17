package com.launchdarkly.client.files;

import com.launchdarkly.client.InMemoryFeatureStore;
import com.launchdarkly.client.LDConfig;
import com.launchdarkly.client.interfaces.FeatureStore;
import com.launchdarkly.client.interfaces.UpdateProcessor;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Future;

import static com.launchdarkly.client.DataModel.DataKinds.FEATURES;
import static com.launchdarkly.client.DataModel.DataKinds.SEGMENTS;
import static com.launchdarkly.client.files.FileComponents.fileDataSource;
import static com.launchdarkly.client.files.TestData.ALL_FLAG_KEYS;
import static com.launchdarkly.client.files.TestData.ALL_SEGMENT_KEYS;
import static com.launchdarkly.client.files.TestData.getResourceContents;
import static com.launchdarkly.client.files.TestData.resourceFilePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

@SuppressWarnings("javadoc")
public class FileDataSourceTest {
  private static final Path badFilePath = Paths.get("no-such-file.json");
  
  private final FeatureStore store = new InMemoryFeatureStore();
  private final LDConfig config = new LDConfig.Builder().build();
  private final FileDataSourceFactory factory;
  
  public FileDataSourceTest() throws Exception {
    factory = makeFactoryWithFile(resourceFilePath("all-properties.json"));
  }
  
  private static FileDataSourceFactory makeFactoryWithFile(Path path) {
    return fileDataSource().filePaths(path);
  }
  
  @Test
  public void flagsAreNotLoadedUntilStart() throws Exception {
    try (UpdateProcessor fp = factory.createUpdateProcessor("", config, store)) {
      assertThat(store.initialized(), equalTo(false));
      assertThat(store.all(FEATURES).size(), equalTo(0));
      assertThat(store.all(SEGMENTS).size(), equalTo(0));
    }
  }
  
  @Test
  public void flagsAreLoadedOnStart() throws Exception {
    try (UpdateProcessor fp = factory.createUpdateProcessor("", config, store)) {
      fp.start();
      assertThat(store.initialized(), equalTo(true));
      assertThat(store.all(FEATURES).keySet(), equalTo(ALL_FLAG_KEYS));
      assertThat(store.all(SEGMENTS).keySet(), equalTo(ALL_SEGMENT_KEYS));
    }
  }
  
  @Test
  public void startFutureIsCompletedAfterSuccessfulLoad() throws Exception {
    try (UpdateProcessor fp = factory.createUpdateProcessor("", config, store)) {
      Future<Void> future = fp.start();
      assertThat(future.isDone(), equalTo(true));
    }
  }
  
  @Test
  public void initializedIsTrueAfterSuccessfulLoad() throws Exception {
    try (UpdateProcessor fp = factory.createUpdateProcessor("", config, store)) {
      fp.start();
      assertThat(fp.initialized(), equalTo(true));
    }
  }
  
  @Test
  public void startFutureIsCompletedAfterUnsuccessfulLoad() throws Exception {
    factory.filePaths(badFilePath);
    try (UpdateProcessor fp = factory.createUpdateProcessor("", config, store)) {
      Future<Void> future = fp.start();
      assertThat(future.isDone(), equalTo(true));
    }
  }
  
  @Test
  public void initializedIsFalseAfterUnsuccessfulLoad() throws Exception {
    factory.filePaths(badFilePath);
    try (UpdateProcessor fp = factory.createUpdateProcessor("", config, store)) {
      fp.start();
      assertThat(fp.initialized(), equalTo(false));
    }
  }
  
  @Test
  public void modifiedFileIsNotReloadedIfAutoUpdateIsOff() throws Exception {
    File file = makeTempFlagFile();
    FileDataSourceFactory factory1 = makeFactoryWithFile(file.toPath());
    try {
      setFileContents(file, getResourceContents("flag-only.json"));
      try (UpdateProcessor fp = factory1.createUpdateProcessor("", config, store)) {
        fp.start();
        setFileContents(file, getResourceContents("segment-only.json"));
        Thread.sleep(400);
        assertThat(store.all(FEATURES).size(), equalTo(1));
        assertThat(store.all(SEGMENTS).size(), equalTo(0));
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
    FileDataSourceFactory factory1 = makeFactoryWithFile(file.toPath()).autoUpdate(true);
    long maxMsToWait = 10000;
    try {
      setFileContents(file, getResourceContents("flag-only.json"));  // this file has 1 flag
      try (UpdateProcessor fp = factory1.createUpdateProcessor("", config, store)) {
        fp.start();
        Thread.sleep(1000);
        setFileContents(file, getResourceContents("all-properties.json"));  // this file has all the flags
        long deadline = System.currentTimeMillis() + maxMsToWait;
        while (System.currentTimeMillis() < deadline) {
          if (store.all(FEATURES).size() == ALL_FLAG_KEYS.size()) {
            // success
            return;
          }
          Thread.sleep(500);
        }
        fail("Waited " + maxMsToWait + "ms after modifying file and it did not reload");
      }
    } finally {
      file.delete();
    }
  }
  
  @Test
  public void ifFilesAreBadAtStartTimeAutoUpdateCanStillLoadGoodDataLater() throws Exception {
    File file = makeTempFlagFile();
    setFileContents(file, "not valid");
    FileDataSourceFactory factory1 = makeFactoryWithFile(file.toPath()).autoUpdate(true);
    long maxMsToWait = 10000;
    try {
      try (UpdateProcessor fp = factory1.createUpdateProcessor("", config, store)) {
        fp.start();
        Thread.sleep(1000);
        setFileContents(file, getResourceContents("flag-only.json"));  // this file has 1 flag
        long deadline = System.currentTimeMillis() + maxMsToWait;
        while (System.currentTimeMillis() < deadline) {
          if (store.all(FEATURES).size() > 0) {
            // success
            return;
          }
          Thread.sleep(500);
        }
        fail("Waited " + maxMsToWait + "ms after modifying file and it did not reload");
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
