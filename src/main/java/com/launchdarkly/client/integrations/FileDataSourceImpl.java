package com.launchdarkly.client.integrations;

import com.google.common.util.concurrent.Futures;
import com.google.gson.JsonElement;
import com.launchdarkly.client.DataModel;
import com.launchdarkly.client.integrations.FileDataSourceParsing.FileDataException;
import com.launchdarkly.client.integrations.FileDataSourceParsing.FlagFactory;
import com.launchdarkly.client.integrations.FileDataSourceParsing.FlagFileParser;
import com.launchdarkly.client.integrations.FileDataSourceParsing.FlagFileRep;
import com.launchdarkly.client.interfaces.FeatureStore;
import com.launchdarkly.client.interfaces.UpdateProcessor;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Implements taking flag data from files and putting it into the data store, at startup time and
 * optionally whenever files change.
 */
final class FileDataSourceImpl implements UpdateProcessor {
  private static final Logger logger = LoggerFactory.getLogger(FileDataSourceImpl.class);

  private final FeatureStore store;
  private final DataLoader dataLoader;
  private final AtomicBoolean inited = new AtomicBoolean(false);
  private final FileWatcher fileWatcher;
  
  FileDataSourceImpl(FeatureStore store, List<Path> sources, boolean autoUpdate) {
    this.store = store;
    this.dataLoader = new DataLoader(sources);

    FileWatcher fw = null;
    if (autoUpdate) {
      try {
        fw = FileWatcher.create(dataLoader.getFiles());
      } catch (IOException e) {
        logger.error("Unable to watch files for auto-updating: " + e);
        fw = null;
      }
    }
    fileWatcher = fw;
  }
  
  @Override
  public Future<Void> start() {
    final Future<Void> initFuture = Futures.immediateFuture(null);
    
    reload();
    
    // Note that if reload() finds any errors, it will not set our status to "initialized". But we
    // will still do all the other startup steps, because we still might end up getting valid data
    // if we are told to reload by the file watcher.

    if (fileWatcher != null) {
      fileWatcher.start(new Runnable() {
        public void run() {
          FileDataSourceImpl.this.reload();
        }
      });
    }
    
    return initFuture;
  }

  private boolean reload() {
    DataBuilder builder = new DataBuilder(); 
    try {
      dataLoader.load(builder); 
    } catch (FileDataException e) {
      logger.error(e.getDescription());
      return false;
    }
    store.init(builder.build());
    inited.set(true);
    return true;
  }
  
  @Override
  public boolean initialized() {
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
    private Thread thread;
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
      thread = new Thread(this, FileDataSourceImpl.class.getName());
      thread.setDaemon(true);
      thread.start();
    }
    
    public void stop() {
      stopped = true;
      if (thread != null) {
        thread.interrupt();
      }
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
            for (Map.Entry<String, JsonElement> e: fileContents.flags.entrySet()) {
              builder.add(DataModel.DataKinds.FEATURES, FlagFactory.flagFromJson(e.getValue()));
            }
          }
          if (fileContents.flagValues != null) {
            for (Map.Entry<String, JsonElement> e: fileContents.flagValues.entrySet()) {
              builder.add(DataModel.DataKinds.FEATURES, FlagFactory.flagWithValue(e.getKey(), e.getValue()));
            }
          }
          if (fileContents.segments != null) {
            for (Map.Entry<String, JsonElement> e: fileContents.segments.entrySet()) {
              builder.add(DataModel.DataKinds.SEGMENTS, FlagFactory.segmentFromJson(e.getValue()));
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
    private final Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData = new HashMap<>();
    
    public Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> build() {
      return allData;
    }
    
    public void add(VersionedDataKind<?> kind, VersionedData item) throws FileDataException {
      @SuppressWarnings("unchecked")
      Map<String, VersionedData> items = (Map<String, VersionedData>)allData.get(kind);
      if (items == null) {
        items = new HashMap<String, VersionedData>();
        allData.put(kind, items);
      }
      if (items.containsKey(item.getKey())) {
        throw new FileDataException("in " + kind.getNamespace() + ", key \"" + item.getKey() + "\" was already defined", null, null);
      }
      items.put(item.getKey(), item);
    }
  }
}
