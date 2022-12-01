package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogValues;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.integrations.FileDataSourceBuilder.SourceInfo;
import com.launchdarkly.sdk.server.integrations.FileDataSourceParsing.FileDataException;
import com.launchdarkly.sdk.server.integrations.FileDataSourceParsing.FlagFactory;
import com.launchdarkly.sdk.server.integrations.FileDataSourceParsing.FlagFileParser;
import com.launchdarkly.sdk.server.integrations.FileDataSourceParsing.FlagFileRep;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataSourceUpdateSink;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Implements taking flag data from files and putting it into the data store, at startup time and
 * optionally whenever files change.
 */
final class FileDataSourceImpl implements DataSource {
  private final DataSourceUpdateSink dataSourceUpdates;
  private final DataLoader dataLoader;
  private final FileData.DuplicateKeysHandling duplicateKeysHandling;
  private final AtomicBoolean inited = new AtomicBoolean(false);
  private final FileWatcher fileWatcher;
  private final LDLogger logger;
  
  FileDataSourceImpl(
      DataSourceUpdateSink dataSourceUpdates,
      List<SourceInfo> sources,
      boolean autoUpdate,
      FileData.DuplicateKeysHandling duplicateKeysHandling,
      LDLogger logger
      ) {
    this.dataSourceUpdates = dataSourceUpdates;
    this.dataLoader = new DataLoader(sources);
    this.duplicateKeysHandling = duplicateKeysHandling;
    this.logger = logger;

    FileWatcher fw = null;
    if (autoUpdate) {
      try {
        fw = FileWatcher.create(dataLoader.getSources(), logger);
      } catch (IOException e) {
        // COVERAGE: there is no way to simulate this condition in a unit test
        logger.error("Unable to watch files for auto-updating: {}", e.toString());
        logger.debug(e.toString(), e);
        fw = null;
      }
    }
    fileWatcher = fw;
  }
  
  @Override
  public Future<Void> start() {
    final Future<Void> initFuture = CompletableFuture.completedFuture(null);
    
    reload();
    
    // Note that if reload() finds any errors, it will not set our status to "initialized". But we
    // will still do all the other startup steps, because we still might end up getting valid data
    // if we are told to reload by the file watcher.

    if (fileWatcher != null) {
      fileWatcher.start(this::reload);
    }
    
    return initFuture;
  }

  private boolean reload() {
    DataBuilder builder = new DataBuilder(duplicateKeysHandling); 
    try {
      dataLoader.load(builder); 
    } catch (FileDataException e) {
      logger.error(e.getDescription());
      dataSourceUpdates.updateStatus(State.INTERRUPTED,
          new ErrorInfo(ErrorKind.INVALID_DATA, 0, e.getDescription(), Instant.now()));
      return false;
    }
    dataSourceUpdates.init(builder.build());
    dataSourceUpdates.updateStatus(State.VALID, null);
    inited.set(true);
    return true;
  }
  
  @Override
  public boolean isInitialized() {
    return inited.get();
  }

  @Override
  public void close() throws IOException {
    if (fileWatcher != null) {
      fileWatcher.stop();
    }
  }
  
  /**
   * If auto-updating is enabled, this component watches for file changes on a worker thread.
   */
  private static final class FileWatcher implements Runnable {
    private final WatchService watchService;
    private final Set<Path> watchedFilePaths;
    private Runnable fileModifiedAction;
    private final Thread thread;
    private final LDLogger logger;
    private volatile boolean stopped;

    private static FileWatcher create(Iterable<SourceInfo> sources, LDLogger logger) throws IOException {
      Set<Path> directoryPaths = new HashSet<>();
      Set<Path> absoluteFilePaths = new HashSet<>();
      FileSystem fs = FileSystems.getDefault();
      WatchService ws = fs.newWatchService();
      
      // In Java, you watch for filesystem changes at the directory level, not for individual files.
      for (SourceInfo s: sources) {
        Path p = s.toFilePath();
        if (p != null) {
          absoluteFilePaths.add(p);
          directoryPaths.add(p.getParent()); 
        }
      }
      for (Path d: directoryPaths) {
        d.register(ws, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
      }
      
      return new FileWatcher(ws, absoluteFilePaths, logger);
    }
    
    private FileWatcher(WatchService watchService, Set<Path> watchedFilePaths, LDLogger logger) {
      this.watchService = watchService;
      this.watchedFilePaths = watchedFilePaths;
      this.logger = logger;
      
      thread = new Thread(this, FileDataSourceImpl.class.getName());
      thread.setDaemon(true);
    }
    
    public void run() {
      while (!stopped) {
        try {
          WatchKey key = watchService.take(); // blocks until a change is available or we are interrupted
          boolean watchedFileWasChanged = false;
          for (WatchEvent<?> event: key.pollEvents()) {
            Watchable w = key.watchable();
            Object context = event.context();
            if (w instanceof Path && context instanceof Path) {
              Path dirPath = (Path)w;
              Path fileNamePath = (Path)context;
              Path absolutePath = dirPath.resolve(fileNamePath);
              if (watchedFilePaths.contains(absolutePath)) {
                watchedFileWasChanged = true;
                break;
              }
            }
          }
          if (watchedFileWasChanged) {
            try {
              fileModifiedAction.run();
            } catch (Exception e) {
              // COVERAGE: there is no way to simulate this condition in a unit test
              logger.warn("Unexpected exception when reloading file data: {}", LogValues.exceptionSummary(e));
            }
          }
          key.reset(); // if we don't do this, the watch on this key stops working
        } catch (InterruptedException e) {
          // if we've been stopped we will drop out at the top of the while loop
        }
      }
    }
    
    public void start(Runnable fileModifiedAction) {
      this.fileModifiedAction = fileModifiedAction;
      thread.start();
    }
    
    public void stop() {
      stopped = true;
      thread.interrupt();
    }
  }
  
  /**
   * Implements the loading of flag data from one or more files. Will throw an exception if any file can't
   * be read or parsed, or if any flag or segment keys are duplicates.
   */
  static final class DataLoader {
    private final List<SourceInfo> sources;
    private final AtomicInteger lastVersion;

    public DataLoader(List<SourceInfo> sources) {
      this.sources = new ArrayList<>(sources);
      this.lastVersion = new AtomicInteger(0);
    }
    
    public Iterable<SourceInfo> getSources() {
      return sources;
    }
    
    public void load(DataBuilder builder) throws FileDataException
    {
      int version = lastVersion.incrementAndGet();
      for (SourceInfo s: sources) {
        try {
          byte[] data = s.readData();
          FlagFileParser parser = FlagFileParser.selectForContent(data);
          FlagFileRep fileContents = parser.parse(new ByteArrayInputStream(data));
          if (fileContents.flags != null) {
            for (Map.Entry<String, LDValue> e: fileContents.flags.entrySet()) {
              builder.add(FEATURES, e.getKey(), FlagFactory.flagFromJson(e.getValue(), version));
            }
          }
          if (fileContents.flagValues != null) {
            for (Map.Entry<String, LDValue> e: fileContents.flagValues.entrySet()) {
              builder.add(FEATURES, e.getKey(), FlagFactory.flagWithValue(e.getKey(), e.getValue(), version));
            }
          }
          if (fileContents.segments != null) {
            for (Map.Entry<String, LDValue> e: fileContents.segments.entrySet()) {
              builder.add(SEGMENTS, e.getKey(), FlagFactory.segmentFromJson(e.getValue(), version));
            }
          }
        } catch (FileDataException e) {
          throw new FileDataException(e.getMessage(), e.getCause(), s);
        } catch (IOException e) {
          throw new FileDataException(null, e, s);
        }
      }
    }
  }
  
  /**
   * Internal data structure that organizes flag/segment data into the format that the feature store
   * expects. Will throw an exception if we try to add the same flag or segment key more than once.
   */
  static final class DataBuilder {
    private final Map<DataKind, Map<String, ItemDescriptor>> allData = new HashMap<>();
    private final FileData.DuplicateKeysHandling duplicateKeysHandling;
    
    public DataBuilder(FileData.DuplicateKeysHandling duplicateKeysHandling) {
      this.duplicateKeysHandling = duplicateKeysHandling;
    }
    
    public FullDataSet<ItemDescriptor> build() {
      ImmutableList.Builder<Map.Entry<DataKind, KeyedItems<ItemDescriptor>>> allBuilder = ImmutableList.builder();
      for (Map.Entry<DataKind, Map<String, ItemDescriptor>> e0: allData.entrySet()) {
        allBuilder.add(new AbstractMap.SimpleEntry<>(e0.getKey(), new KeyedItems<>(e0.getValue().entrySet())));
      }
      return new FullDataSet<>(allBuilder.build());
    }
    
    public void add(DataKind kind, String key, ItemDescriptor item) throws FileDataException {
      Map<String, ItemDescriptor> items = allData.get(kind);
      if (items == null) {
        items = new HashMap<String, ItemDescriptor>();
        allData.put(kind, items);
      }
      if (items.containsKey(key)) {
        if (duplicateKeysHandling == FileData.DuplicateKeysHandling.IGNORE) {
          return;
        }
        throw new FileDataException("in " + kind.getName() + ", key \"" + key + "\" was already defined", null, null);
      }
      items.put(key, item);
    }
  }
}
