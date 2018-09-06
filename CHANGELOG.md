# Change log


All notable changes to the LaunchDarkly Java SDK will be documented in this file. This project adheres to [Semantic Versioning](http://semver.org).

## [4.3.1] - 2018-09-04
### Fixed:
- When evaluating a prerequisite feature flag, the analytics event for the evaluation did not include the result value if the prerequisite flag was off.
- The default Gson serialization for `LDUser` now includes all user properties. Previously, it omitted `privateAttributeNames`.

## [4.3.0] - 2018-08-27
### Added:
- The new `LDClient` method `allFlagsState()` should be used instead of `allFlags()` if you are passing flag data to the front end for use with the JavaScript SDK. It preserves some flag metadata that the front end requires in order to send analytics events correctly. Versions 2.5.0 and above of the JavaScript SDK are able to use this metadata, but the output of `allFlagsState()` will still work with older versions.
- The `allFlagsState()` method also allows you to select only client-side-enabled flags to pass to the front end, by using the option `FlagsStateOption.CLIENT_SIDE_ONLY`. ([#112](https://github.com/launchdarkly/java-client/issues/112))
- The new `LDClient` methods `boolVariationDetail`, `intVariationDetail`, `doubleVariationDetail`, `stringVariationDetail`, and `jsonVariationDetail` allow you to evaluate a feature flag (using the same parameters as you would for `boolVariation`, etc.) and receive more information about how the value was calculated. This information is returned in an `EvaluationDetail` object, which contains both the result value and an `EvaluationReason` which will tell you, for instance, if the user was individually targeted for the flag or was matched by one of the flag's rules, or if the flag returned the default value due to an error.

### Fixed:
- Fixed a bug in `LDUser.Builder` that would throw an exception if you initialized the builder by copying an existing user, and then tried to add a custom attribute.

### Deprecated:
- `LDClient.allFlags()`

## [4.2.2] - 2018-08-17
### Fixed:
- When logging errors related to the evaluation of a specific flag, the log message now always includes the flag key.
- Exception stacktraces are now logged only at DEBUG level. Previously, some were being logged at ERROR level.

## [4.2.1] - 2018-07-16
### Fixed:
- Should not permanently give up on posting events if the server returns a 400 error.
- Fixed a bug in the Redis store that caused an unnecessary extra Redis query (and a debug-level log message about updating a flag with the same version) after every update of a flag.

## [4.2.0] - 2018-06-26
### Added:
- New overloads of `LDUser.Builder.custom` and `LDUser.Builder.privateCustom` allow you to set a custom attribute value to any JSON element.

### Changed:
- The client now treats most HTTP 4xx errors as unrecoverable: that is, after receiving such an error, it will not make any more HTTP requests for the lifetime of the client instance, in effect taking the client offline. This is because such errors indicate either a configuration problem (invalid SDK key) or a bug, which is not likely to resolve without a restart or an upgrade. This does not apply if the error is 400, 408, 429, or any 5xx error.
- During initialization, if the client receives any of the unrecoverable errors described above, the client constructor will return immediately; previously it would continue waiting until a timeout. The `initialized()` method will return false in this case.

## [4.1.0] - 2018-05-15

### Added:
- The new user builder methods `customValues` and `privateCustomValues` allow you to add a custom user attribute with multiple JSON values of mixed types. ([#126](https://github.com/launchdarkly/java-client/issues/126))
- The new constant `VersionedDataKind.ALL` is a list of all existing `VersionedDataKind` instances. This is mainly useful if you are writing a custom `FeatureStore` implementation.

## [4.0.0] - 2018-05-10

### Changed:
- To reduce the network bandwidth used for analytics events, feature request events are now sent as counters rather than individual events, and user details are now sent only at intervals rather than in each event. These behaviors can be modified through the LaunchDarkly UI and with the new configuration option `inlineUsersInEvents`. For more details, see [Analytics Data Stream Reference](https://docs.launchdarkly.com/v2.0/docs/analytics-data-stream-reference).
- When sending analytics events, if there is a connection error or an HTTP 5xx response, the client will try to send the events again one more time after a one-second delay.
- The `LdClient` class is now `final`.

### Added:
- New methods on `LDConfig.Builder` (`updateProcessorFactory`, `featureStoreFactory`, `eventProcessorFactory`) allow you to specify different implementations of each of the main client subcomponents (receiving feature state, storing feature state, and sending analytics events) for testing or for any other purpose. The `Components` class provides factories for all built-in implementations of these.

### Deprecated:
- The `featureStore` configuration method is deprecated, replaced by the new factory-based mechanism described above.


## [3.0.3] - 2018-03-26
### Fixed
* In the Redis feature store, fixed a synchronization problem that could cause a feature flag update to be missed if several of them happened in rapid succession.
* Fixed a bug that would cause a `NullPointerException` when trying to evaluate a flag rule that contained an unknown operator type. This could happen if you started using some recently added feature flag functionality in the LaunchDarkly application but had not yet upgraded the SDK to a version that supports that feature. In this case, it should now simply treat that rule as a non-match.

### Changed
* The log message "Attempted to update ... with a version that is the same or older" has been downgraded from `WARN` level to `DEBUG`. It can happen frequently in normal operation when the client is in streaming mode, and is not a cause for concern.

## [3.0.2] - 2018-03-01
### Fixed
- Improved performance when evaluating flags with custom attributes, by avoiding an unnecessary caught exception (thanks, [rbalamohan](https://github.com/launchdarkly/java-client/issues/113)).


## [3.0.1] - 2018-02-22
### Added
- Support for a new LaunchDarkly feature: reusable user segments.

### Changed
- The `FeatureStore` interface has been changed to support user segment data as well as feature flags. Existing code that uses `InMemoryFeatureStore` or `RedisFeatureStore` should work as before, but custom feature store implementations will need to be updated.
- Removed deprecated methods.


## [3.0.0] - 2018-02-21

_This release was broken and should not be used._


## [2.6.1] - 2018-03-01
### Fixed
- Improved performance when evaluating flags with custom attributes, by avoiding an unnecessary caught exception (thanks, [rbalamohan](https://github.com/launchdarkly/java-client/issues/113)).


## [2.6.0] - 2018-02-12
## Added
- Adds support for a future LaunchDarkly feature, coming soon: semantic version user attributes.

## Changed
- It is now possible to compute rollouts based on an integer attribute of a user, not just a string attribute.


## [2.5.1] - 2018-01-31

## Changed
- All threads created by the client are now daemon threads. 
- Fixed a bug that could result in a previously deleted feature flag appearing to be available again.
- Reduced the logging level for use of an unknown feature flag from `WARN` to `INFO`.


## [2.5.0] - 2018-01-08
## Added
- Support for specifying [private user attributes](https://docs.launchdarkly.com/docs/private-user-attributes) in order to prevent user attributes from being sent in analytics events back to LaunchDarkly. See the `allAttributesPrivate` and `privateAttributeNames` methods on `LDConfig.Builder` as well as the `privateX` methods on `LDUser.Builder`.

## [2.4.0] - 2017-12-20
## Changed
- Added an option to disable sending analytics events
- No longer attempt to reconnect if a 401 response is received (this would indicate an invalid SDK key, so retrying won't help)
- Simplified logic to detect dropped stream connections
- Increased default polling interval to 30s
- Use flag data in redis before stream connection is established, if possible (See #107)
- Avoid creating HTTP cache when streaming mode is enabled (as it won't be useful). This makes it possible to use the SDK in Google App Engine and other environments with no mutable disk access.


## [2.3.4] - 2017-10-25
## Changed
- Removed GSON dependency from default jar (fixes #103)

## [2.3.2] - 2017-09-22
## Changed
- Only log a warning on the first event that overflows the event buffer [#102]

## [2.3.1] - 2017-08-11
## Changed
- Updated okhttp-eventsource dependency to 1.5.0 to pick up better connection timeout handling.

## [2.3.0] - 2017-07-10
## Added
- LDUser `Builder` constructor which accepts a previously built user as an initialization parameter.


## [2.2.6] - 2017-06-16
## Added
- #96 `LDUser` now has `equals()` and `hashCode()` methods

## Changed
- #93 `LDClient` now releases resources more quickly when shutting down

## [2.2.5] - 2017-06-02
## Changed
- Improved Gson compatibility (added no-args constructors for classes we deserialize)
- Automated release process 


## [2.2.4] - 2017-06-02
## Changed
- Improved Gson compatibility (added no-args constructors for classes we deserialize)
- Automated release process 


## [2.2.3] - 2017-05-10
### Fixed
- Fixed issue where stream connection failed to fully establish

## [2.2.2] - 2017-05-05
### Fixed
- In Java 7, connections to LaunchDarkly are now possible using TLSv1.1 and/or TLSv1.2
- The order of SSE stream events is now preserved. ([launchdarkly/okhttp-eventsource#19](https://github.com/launchdarkly/okhttp-eventsource/issues/19))

## [2.2.1] - 2017-04-25
### Fixed
- [#92](https://github.com/launchdarkly/java-client/issues/92) Regex `matches` targeting rules now include the user if
a match is found anywhere in the attribute.  Before fixing this bug, the entire attribute needed to match the pattern.

## [2.2.0] - 2017-04-11
### Added
- Authentication for proxied http requests is now supported (Basic Auth only)

### Changed
- Improved Redis connection pool management.

## [2.1.0] - 2017-03-02
### Added
- LdClientInterface (and its implementation) have a new method: `boolean isFlagKnown(String featureKey)` which checks for a 
feature flag's existence. Thanks @yuv422!

## [2.0.11] - 2017-02-24
### Changed
- EventProcessor now respects the connect and socket timeouts configured with LDConfig.

## [2.0.10] - 2017-02-06
### Changed
- Updated okhttp-eventsource dependency to bring in newer okhttp dependency
- Added more verbose debug level logging when sending events

## [2.0.9] - 2017-01-24
### Changed
- StreamProcessor uses the proxy configuration specified by LDConfig.

## [2.0.8] - 2016-12-22
### Changed
- Better handling of null default values.

## [2.0.7] - 2016-12-21
### Changed
- allFlags() method on client no longer returns null when client is in offline mode.

## [2.0.6] - 2016-11-21
### Changed
- RedisFeatureStore: Update Jedis dependency. Improved thread/memory management.

## [2.0.5] - 2016-11-09
### Changed
- The StreamProcessor now listens for heartbeats from the streaming API, and will automatically reconnect if heartbeats are not received.

## [2.0.4] - 2016-10-12
### Changed
- Updated GSON dependency version to 2.7

## [2.0.3] - 2016-10-10
### Added
- StreamingProcessor now supports increasing retry delays with jitter. Addresses [https://github.com/launchdarkly/java-client/issues/74[(https://github.com/launchdarkly/java-client/issues/74)

## [2.0.2] - 2016-09-13
### Added
- Now publishing artifact with 'all' classifier that includes SLF4J for ColdFusion or other systems that need it.

## [2.0.1] - 2016-08-12
### Removed
- Removed slf4j from default artifact: [#71](https://github.com/launchdarkly/java-client/issues/71)

## [2.0.0] - 2016-08-08
### Added
- Support for multivariate feature flags. New methods `boolVariation`, `jsonVariation` and `intVariation` and `doubleVariation` for multivariates.
- Added `LDClientInterface`, an interface suitable for mocking `LDClient`.

### Changed
- The `Feature` data model has been replaced with `FeatureFlag`. `FeatureFlag` is not generic.
- The `allFlags` method now returns a `Map<String, JsonElement>` to support multivariate flags.

### Deprecated
- The `toggle` call has been deprecated in favor of `boolVariation`.

### Removed
- The `getFlag` call has been removed.
- The `debugStreaming` configuration option has been removed.
