package com.launchdarkly.client.files;

/**
 * The entry point for the file data source, which allows you to use local files as a source of
 * feature flag state. This would typically be used in a test environment, to operate using a
 * predetermined feature flag state without an actual LaunchDarkly connection.
 * <p>
 * To use this component, call {@link #fileDataSource()} to obtain a factory object, call one or
 * methods to configure it, and then add it to your LaunchDarkly client configuration. At a
 * minimum, you will want to call {@link FileDataSourceFactory#filePaths(String...)} to specify
 * your data file(s); you can also use {@link FileDataSourceFactory#autoUpdate(boolean)} to
 * specify that flags should be reloaded when a file is modified. See {@link FileDataSourceFactory}
 * for all configuration options.
 * <pre>
 *     FileDataSourceFactory f = FileComponents.fileDataSource()
 *         .filePaths("./testData/flags.json")
 *         .autoUpdate(true);
 *     LDConfig config = new LDConfig.Builder()
 *         .updateProcessorFactory(f)
 *         .build();
 * </pre>
 * <p>
 * This will cause the client <i>not</i> to connect to LaunchDarkly to get feature flags. The
 * client may still make network connections to send analytics events, unless you have disabled
 * this with {@link com.launchdarkly.client.LDConfig.Builder#sendEvents(boolean)} or
 * {@link com.launchdarkly.client.LDConfig.Builder#offline(boolean)}.
 * <p>
 * Flag data files can be either JSON or YAML. They contain an object with three possible
 * properties:
 * <ul>
 * <li> {@code flags}: Feature flag definitions.
 * <li> {@code flagVersions}: Simplified feature flags that contain only a value.
 * <li> {@code segments}: User segment definitions.
 * </ul>
 * <p>
 * The format of the data in {@code flags} and {@code segments} is defined by the LaunchDarkly application
 * and is subject to change. Rather than trying to construct these objects yourself, it is simpler
 * to request existing flags directly from the LaunchDarkly server in JSON format, and use this
 * output as the starting point for your file. In Linux you would do this:
 * <pre>
 *     curl -H "Authorization: <your sdk key>" https://app.launchdarkly.com/sdk/latest-all
 * </pre>
 * <p>
 * The output will look something like this (but with many more properties):
 * <pre>
 * {
 *     "flags": {
 *         "flag-key-1": {
 *             "key": "flag-key-1",
 *             "on": true,
 *             "variations": [ "a", "b" ]
 *         },
 *         "flag-key-2": {
 *             "key": "flag-key-2",
 *             "on": true,
 *             "variations": [ "c", "d" ]
 *         }
 *     },
 *     "segments": {
 *         "segment-key-1": {
 *             "key": "segment-key-1",
 *             "includes": [ "user-key-1" ]
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * Data in this format allows the SDK to exactly duplicate all the kinds of flag behavior supported
 * by LaunchDarkly. However, in many cases you will not need this complexity, but will just want to
 * set specific flag keys to specific values. For that, you can use a much simpler format:
 * <pre>
 * {
 *     "flagValues": {
 *         "my-string-flag-key": "value-1",
 *         "my-boolean-flag-key": true,
 *         "my-integer-flag-key": 3
 *     }
 * }
 * </pre>
 * <p>
 * Or, in YAML:
 * <pre>
 * flagValues:
 *   my-string-flag-key: "value-1"
 *   my-boolean-flag-key: true
 *   my-integer-flag-key: 3
 * </pre>
 * <p>
 * It is also possible to specify both {@code flags} and {@code flagValues}, if you want some flags
 * to have simple values and others to have complex behavior. However, it is an error to use the
 * same flag key or segment key more than once, either in a single file or across multiple files.
 * <p>
 * If the data source encounters any error in any file-- malformed content, a missing file, or a
 * duplicate key-- it will not load flags from any of the files.
 * 
 * @since 4.5.0
 */
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
