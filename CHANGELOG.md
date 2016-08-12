# Change log


All notable changes to the LaunchDarkly Java SDK will be documented in this file. This project adheres to [Semantic Versioning](http://semver.org).

## [2.0.0] - 2016-08-08
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