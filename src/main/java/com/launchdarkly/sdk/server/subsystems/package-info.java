/**
 * Interfaces for implementation of LaunchDarkly SDK components.
 * <p>
 * Most applications will not need to refer to these types. You will use them if you are creating a
 * plugin component, such as a database integration. They are also used as interfaces for the built-in
 * SDK components, so that plugin components can be used interchangeably with those: for instance, the
 * configuration method {@link com.launchdarkly.sdk.server.LDConfig.Builder#dataStore(ComponentConfigurer)}
 * references {@link com.launchdarkly.sdk.server.subsystems.DataStore} as an abstraction for the data
 * store component.
 * <p>
 * The package also includes concrete types that are used as parameters within these interfaces.
 */
package com.launchdarkly.sdk.server.subsystems;
