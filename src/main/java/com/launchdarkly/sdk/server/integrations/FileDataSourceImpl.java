package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.integrations.FileDataSourceParsing.FileDataException;
import com.launchdarkly.sdk.server.integrations.FileDataSourceParsing.FlagFactory;
import com.launchdarkly.sdk.server.integrations.FileDataSourceParsing.FlagFileParser;
import com.launchdarkly.sdk.server.integrations.FileDataSourceParsing.FlagFileRep;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.KeyedItems;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
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
  private static final Logger logger = LoggerFactory.getLogger(FileDataSourceImpl.class);

  private final DataSourceUpdates dataSourceUpdates;
  private final DataLoader dataLoader;
  private final AtomicBoolean inited = new AtomicBoolean(false);
  private final FileWatcher fileWatcher;
  
  FileDataSourceImpl(DataSourceUpdates dataSourceUpdates, List<Path> sources, boolean autoUpdate) {
    this.dataSourceUpdates = dataSourceUpdates;
    this.dataLoader = new DataLoader(sources);

    FileWatcher fw = null;
    if (autoUpdate) {
      try {
        fw = FileWatcher.create(dataLoader.getFiles());
      } catch (IOException e) {
        // COVERAGE: there is no way to simulate this condition in a unit test
        logger.error("Unable to watch files for auto-updating: " + e);
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
    DataBuilder builder = new DataBuilder(); 
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
    private volatile boolean stopped;

    private static FileWatcher create(Iterable<Path> files) throws IOException {
      Set<Path> directoryPaths = new HashSet<>();
      Set<Path> absoluteFilePaths = new HashSet<>();
      FileSystem fs = FileSystems.getDefault();
      WatchService ws = fs.newWatchService();
      
      // In Java, you watch for filesystem changes at the directory level, not for individual files.
      for (Path p: files) {
        absoluteFilePaths.add(p);
        directoryPaths.add(p.getParent());
      }
      for (Path d: directoryPaths) {
        d.register(ws, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
      }
      
      return new FileWatcher(ws, absoluteFilePaths);
    }
    
    private FileWatcher(WatchService watchService, Set<Path> watchedFilePaths) {
      this.watchService = watchService;
      this.watchedFilePaths = watchedFilePaths;
      
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
              logger.warn("Unexpected exception when reloading file data: " + e);
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
    private final List<Path> files;

    public DataLoader(List<Path> files) {
      this.files = new ArrayList<Path>(files);
    }
    
    public Iterable<Path> getFiles() {
      return files;
    }
    
    public void load(DataBuilder builder) throws FileDataException
    {
      for (Path p: files) {
        try {
          byte[] data = Files.readAllBytes(p);
          FlagFileParser parser = FlagFileParser.selectForContent(data);
          FlagFileRep fileContents = parser.parse(new ByteArrayInputStream(data));
          if (fileContents.flags != null) {
            for (Map.Entry<String, LDValue> e: fileContents.flags.entrySet()) {
              builder.add(FEATURES, e.getKey(), FlagFactory.flagFromJson(e.getValue()));
            }
          }
          if (fileContents.flagValues != null) {
            for (Map.Entry<String, LDValue> e: fileContents.flagValues.entrySet()) {
              builder.add(FEATURES, e.getKey(), FlagFactory.flagWithValue(e.getKey(), e.getValue()));
            }
          }
          if (fileContents.segments != null) {
            for (Map.Entry<String, LDValue> e: fileContents.segments.entrySet()) {
              builder.add(SEGMENTS, e.getKey(), FlagFactory.segmentFromJson(e.getValue()));
            }
          }
        } catch (FileDataException e) {
          throw new FileDataException(e.getMessage(), e.getCause(), p);
        } catch (IOException e) {
          throw new FileDataException(null, e, p);
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
        throw new FileDataException("in " + kind.getName() + ", key \"" + key + "\" was already defined", null, null);
      }
      items.put(key, item);
    }
  }
}
