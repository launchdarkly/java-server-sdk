/**
 * This package contains integration tools for connecting the SDK to other software components, or
 * configuring how it connects to LaunchDarkly.
 * <p>
 * In the current main LaunchDarkly Java SDK library, this package contains the configuration builders
 * for the standard SDK components such as {@link com.launchdarkly.sdk.server.integrations.StreamingDataSourceBuilder},
 * the {@link com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder} builder for use with
 * database integrations (the specific database integrations themselves are provided by add-on libraries),
 * and {@link com.launchdarkly.sdk.server.integrations.FileData} (for reading flags from a file in testing).
 */
package com.launchdarkly.sdk.server.integrations;
