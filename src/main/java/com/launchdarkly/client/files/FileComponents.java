package com.launchdarkly.client.files;

/**
 * Deprecated entry point for the file data source.
 * @since 4.5.0
 * @deprecated Use {@link com.launchdarkly.client.integrations.FileData}.
 */
@Deprecated
public abstract class FileComponents {
  /**
   * Creates a {@link FileDataSourceFactory} which you can use to configure the file data
   * source.
   * @return a {@link FileDataSourceFactory}
   */
	public static FileDataSourceFactory fileDataSource() {
		return new FileDataSourceFactory(); 
	}
}
