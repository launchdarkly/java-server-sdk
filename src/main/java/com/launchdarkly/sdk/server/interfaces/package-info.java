/**
 * The package for interfaces that allow customization of LaunchDarkly components, and interfaces
 * to other advanced SDK features. 
 * <p>
 * Most applications will not need to refer to these types. You will use them if you are creating a
 * plug-in component, such as a database integration, or if you use advanced features such as
<<<<<<< HEAD
 * {@link com.launchdarkly.sdk.server.LDClientInterface#getDataStoreStatusProvider()} or
 * {@link com.launchdarkly.sdk.server.LDClientInterface#getFlagTracker()}.
=======
 * {@link com.launchdarkly.sdk.server.interfaces.LDClientInterface#getDataStoreStatusProvider()} or
 * {@link com.launchdarkly.sdk.server.interfaces.LDClientInterface#registerFlagChangeListener(FlagChangeListener)}.
>>>>>>> 5.x
 */
package com.launchdarkly.sdk.server.interfaces;
