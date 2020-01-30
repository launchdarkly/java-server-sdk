package com.launchdarkly.client.files;

import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.LDConfig;
import com.launchdarkly.client.UpdateProcessor;
import com.launchdarkly.client.UpdateProcessorFactory;
import com.launchdarkly.client.integrations.FileDataSourceBuilder;
import com.launchdarkly.client.integrations.FileData;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Deprecated name for {@link FileDataSourceBuilder}. Use {@link FileData#dataSource()} to obtain the
 * new builder.
 * 
 * @since 4.5.0
 * @deprecated
 */
public class FileDataSourceFactory implements UpdateProcessorFactory {
  private final FileDataSourceBuilder wrappedBuilder = new FileDataSourceBuilder();
  
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
  public FileDataSourceFactory filePaths(String... filePaths) throws InvalidPathException {
    wrappedBuilder.filePaths(filePaths);
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
  public FileDataSourceFactory filePaths(Path... filePaths) {
    wrappedBuilder.filePaths(filePaths);
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
  public FileDataSourceFactory autoUpdate(boolean autoUpdate) {
    wrappedBuilder.autoUpdate(autoUpdate);
    return this;
  }
  
  /**
   * Used internally by the LaunchDarkly client.
   */
  @Override
  public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
    return wrappedBuilder.createUpdateProcessor(sdkKey, config, featureStore);
  }
}