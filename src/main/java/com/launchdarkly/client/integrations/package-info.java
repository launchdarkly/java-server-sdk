/**
 * This package contains integration tools for connecting the SDK to other software components.
 * <p>
 * In the current main LaunchDarkly Java SDK library, this package contains {@link com.launchdarkly.client.integrations.Redis}
 * (for using Redis as a store for flag data) and {@link com.launchdarkly.client.integrations.FileData}
 * (for reading flags from a file in testing). Other SDK add-on libraries, such as database integrations,
 * will define their classes in {@code com.launchdarkly.client.integrations} as well.
 * <p>
 * The general pattern for factory methods in this package is {@code ToolName#componentType()},
 * such as {@code Redis#dataStore()} or {@code FileData#dataSource()}.
 */
package com.launchdarkly.client.integrations;
