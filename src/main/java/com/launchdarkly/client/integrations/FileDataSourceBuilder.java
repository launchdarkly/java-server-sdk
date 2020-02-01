package com.launchdarkly.client.integrations;

import com.launchdarkly.client.interfaces.ClientContext;
import com.launchdarkly.client.interfaces.DataSource;
import com.launchdarkly.client.interfaces.DataSourceFactory;
import com.launchdarkly.client.interfaces.DataStore;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * To use the file data source, obtain a new instance of this class with {@link FileData#dataSource()},
 * call the builder method {@link #filePaths(String...)} to specify file path(s),
 * then pass the resulting object to {@link com.launchdarkly.client.LDConfig.Builder#dataSource(DataSourceFactory)}.
 * <p>
 * For more details, see {@link FileData}.
 * 
 * @since 4.12.0
 */
public final class FileDataSourceBuilder implements DataSourceFactory {
  private final List<Path> sources = new ArrayList<>();
  private boolean autoUpdate = false;
  
  /**
   * Adds any number of source files for loading flag data, specifying each file path as a string. The files will
   * not actually be loaded until the LaunchDarkly client starts up.
   * <p> 
   * Files will be parsed as JSON if their first non-whitespace character is '{'. Otherwise, they will be parsed as YAML.
   *
   * @param filePaths path(s) to the source file(s); may be absolute or relative to the current working directory
   * @return the same factory object
   * 
   * @throws InvalidPathException if one of the parameters is not a valid file path
   */
  public FileDataSourceBuilder filePaths(String... filePaths) throws InvalidPathException {
    for (String p: filePaths) {
      sources.add(Paths.get(p));
    }
    return this;
  }

  /**
   * Adds any number of source files for loading flag data, specifying each file path as a Path. The files will
   * not actually be loaded until the LaunchDarkly client starts up.
   * <p> 
   * Files will be parsed as JSON if their first non-whitespace character is '{'. Otherwise, they will be parsed as YAML.
   * 
   * @param filePaths path(s) to the source file(s); may be absolute or relative to the current working directory
   * @return the same factory object
   */
  public FileDataSourceBuilder filePaths(Path... filePaths) {
    for (Path p: filePaths) {
      sources.add(p);
    }
    return this;
  }
  
  /**
   * Specifies whether the data source should watch for changes to the source file(s) and reload flags
   * whenever there is a change. By default, it will not, so the flags will only be loaded once.
   * <p>
   * Note that auto-updating will only work if all of the files you specified have valid directory paths at
   * startup time; if a directory does not exist, creating it later will not result in files being loaded from it. 
   * 
   * @param autoUpdate true if flags should be reloaded whenever a source file changes
   * @return the same factory object
   */
  public FileDataSourceBuilder autoUpdate(boolean autoUpdate) {
    this.autoUpdate = autoUpdate;
    return this;
  }
  
  /**
   * Used internally by the LaunchDarkly client.
   */
  @Override
  public DataSource createDataSource(ClientContext context, DataStore featureStore) {
    return new FileDataSourceImpl(featureStore, sources, autoUpdate);
  }
}