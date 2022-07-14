/**
 * Types that are part of the public API, but are not needed for basic use of the SDK.
 * <p>
 * Types in this namespace include:
 * <ul>
 * <li> The interface {@link com.launchdarkly.sdk.server.interfaces.LDClientInterface}, which
 * allow the SDK client to be referenced via an interface rather than the concrete type
 * {@link com.launchdarkly.sdk.server.LDClient}. </li>
 * <li> Interfaces like {@link com.launchdarkly.sdk.server.interfaces.FlagTracker} that provide a
 * facade for some part of the SDK API; these are returned by methods like
 * {@link com.launchdarkly.sdk.server.LDClient#getFlagTracker()}. </li>
 * <li> Concrete types that are used as parameters within these interfaces, like
 * {@link com.launchdarkly.sdk.server.interfaces.FlagChangeEvent}. </li>
 * </ul>
 */
package com.launchdarkly.sdk.server.interfaces;
