# Change log


All notable changes to the LaunchDarkly Java SDK will be documented in this file. This project adheres to [Semantic Versioning](http://semver.org).

## [2.2.2] - 2017-04-28
### Fixed
- In Java 7, connections to LaunchDarkly are now possible using TLSv1.1 and/or TLSv1.2
- The order of SSE stream events is now preserved. ((launchdarkly/okhttp-eventsource#19)[https://github.com/launchdarkly/okhttp-eventsource/issues/19]

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
