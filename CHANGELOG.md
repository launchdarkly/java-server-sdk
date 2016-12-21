# Change log


All notable changes to the LaunchDarkly Java SDK will be documented in this file. This project adheres to [Semantic Versioning](http://semver.org).
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