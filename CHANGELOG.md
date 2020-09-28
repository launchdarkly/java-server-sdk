# Change log

All notable changes to the LaunchDarkly Java SDK will be documented in this file. This project adheres to [Semantic Versioning](http://semver.org).

## [5.1.0] - 2020-09-04
### Added:
- The `TestData` class in `com.launchdarkly.sdk.server.integrations` is a new way to inject feature flag data programmatically into the SDK for testing—either with fixed values for each flag, or with targets and/or rules that can return different values for different users. Unlike `FileData`, this mechanism does not use any external resources, only the data that your test code has provided.

### Fixed:
- In polling mode, the log message &#34;LaunchDarkly client initialized&#34; was appearing after every successful poll request. It should only appear once.

## [5.0.5] - 2020-09-03
### Fixed:
- Bump SnakeYAML from 1.19 to 1.26 to address CVE-2017-18640. The SDK only parses YAML if the application has configured the SDK with a flag data file, so it&#39;s unlikely this CVE would affect SDK usage as it would require configuration and access to a local file.


## [5.0.4] - 2020-09-01
### Fixed:
- Updated the version of OkHttp contained within the SDK from 4.5.0 to 4.8.1, to address multiple [known issues](https://square.github.io/okhttp/changelog/) including an incompatibility with OpenJDK 8.0.252 under some conditions. ([#204](https://github.com/launchdarkly/java-server-sdk/issues/204))

## [5.0.3] - 2020-08-18
### Fixed:
- A packaging issue with Kotlin dependencies caused problems with IntelliJ code completion and code highlighting. ([#201](https://github.com/launchdarkly/java-server-sdk/issues/201))

## [5.0.2] - 2020-06-25
### Changed:
- It is no longer necessary to set `StreamingDataSourceBuilder.pollingBaseURI` if you are also setting `baseURI`. This is due to a change in how the LaunchDarkly streaming service works. The setter method still exists, but no longer has any effect and will be deprecated in a future release.

### Fixed:
- In polling mode, if a poll request failed due to a temporary network problem but then a subsequent request succeeded, `DataSourceStatusProvider` was continuing to report the status as `INTERRUPTED` when it should have been restored to `VALID`.
- In polling mode, the SDK was unnecessarily re-storing the flag data in the data store even if it had not changed since the last poll request. This would cause unnecessary updates when using a database.
- In polling mode, temporary files used for HTTP caching (in the system temporary directory) were not being cleaned up when the client was closed.
- Fixed incorrect sample code in the documentation comment for `FlagValueChangeListener`.

## [5.0.1] - 2020-06-19
### Fixed:
- Fixed a bug that could cause worker threads for the EventSource stream to persist after closing the client, if the client had shut down the stream due to detecting an invalid SDK key.

## [5.0.0] - 2020-06-02
This is a major rewrite that introduces a cleaner API design, adds new features, and makes the SDK code easier to maintain and extend. See the [Java 4.x to 5.0 migration guide](https://docs.launchdarkly.com/sdk/server-side/java/migration-4-to-5) for an in-depth look at the changes in this version; the following is a summary.
 
(For early adopters who have used the the 5.0.0-rc2 beta release: some things have changed between 5.0.0-rc2 and this full release. The [5.0.0-rc2 release notes](https://github.com/launchdarkly/java-server-sdk/releases/tag/5.0.0-rc2) have been updated with a section describing these changes.)
 
### Added:
- You can tell the SDK to notify you whenever a feature flag&#39;s configuration has changed (either in general, or in terms of its result for a specific user), using `LDClient.getFlagTracker()`. ([#83](https://github.com/launchdarkly/java-server-sdk/issues/83))
- You can monitor the status of the SDK&#39;s data source (which normally means the streaming connection to the LaunchDarkly service) with `LDClient.getDataSourceStatusProvider()`. This allows you to check the current connection status, and to be notified if this status changes. ([#184](https://github.com/launchdarkly/java-server-sdk/issues/184))
- You can monitor the status of a persistent data store with `LDClient.getDataStoreStatusProvider()`. This allows you to check whether database updates are succeeding, to be notified if this status changes, and to get caching statistics.
- The `FileData` tool now supports reading flag data from a classpath resource as if it were a data file. See `FileDataSourceBuilder.classpathResources()`. ([#193](https://github.com/launchdarkly/java-server-sdk/issues/193))
- `LDConfig.Builder.logging()` is a new configuration category for options related to logging. Currently the only such option is `escalateDataSourceOutageLoggingAfter`, which controls the new connection failure logging behavior described below.
- `LDConfig.Builder.threadPriority()` allows you to set the priority for worker threads created by the SDK.
- The `UserAttribute` class provides a less error-prone way to refer to user attribute names in configuration, and can also be used to get an arbitrary attribute from a user.
- The `LDGson` and `LDJackson` classes allow SDK classes like `LDUser` to be easily converted to or from JSON using the popular Gson and Jackson frameworks.
 
### Changed (requirements/dependencies/build):
- The minimum supported Java version is now 8.
- The SDK no longer exposes a Gson dependency or any Gson types.
- Third-party libraries like Gson, Guava, and OkHttp that are used internally by the SDK have been updated to newer versions since Java 7 compatibility is no longer required. ([#158](https://github.com/launchdarkly/java-server-sdk/issues/158))
- Code coverage reports and JMH benchmarks are now generated in every build. Unit test coverage of the entire SDK codebase has been greatly improved.
 
### Changed (API changes):
- Package names have changed: the main SDK classes are now in `com.launchdarkly.sdk` and `com.launchdarkly.sdk.server`.
- Many rarely-used classes and interfaces have been moved out of the main SDK package into `com.launchdarkly.sdk.server.integrations` and `com.launchdarkly.sdk.server.interfaces`.
- The type `java.time.Duration` is now used for configuration properties that represent an amount of time, instead of using a number of milliseconds or seconds.
- `LDClient.initialized()` has been renamed to `isInitialized()`.
- `LDClient.intVariation()` and `doubleVariation()` now return `int` and `double`, not the nullable `Integer` and `Double`.
- `EvaluationDetail.getVariationIndex()` now returns `int` instead of `Integer`.
- `EvaluationReason` is now a single concrete class rather than an abstract base class.
- The component interfaces `FeatureStore` and `UpdateProcessor` have been renamed to `DataStore` and `DataSource`. The factory interfaces for these components now receive SDK configuration options in a different way that does not expose other components&#39; configurations to each other.
- The `PersistentDataStore` interface for creating your own database integrations has been simplified by moving all of the serialization and caching logic into the main SDK code.
 
### Changed (behavioral changes):
- SLF4J logging now uses a simpler, more stable set of logger names instead of using the names of specific implementation classes that are subject to change. General messages are logged under `com.launchdarkly.sdk.server.LDClient`, while messages about specific areas of functionality are logged under that name plus `.DataSource` (streaming, polling, file data, etc.), `.DataStore` (database integrations), `.Evaluation` (unexpected errors during flag evaluations), or `.Events` (analytics event processing).
- If analytics events are disabled with `Components.noEvents()`, the SDK now avoids generating any analytics event objects internally. Previously they were created and then discarded, causing unnecessary heap churn.
- Network failures and server errors for streaming or polling requests were previously logged at `ERROR` level in most cases but sometimes at `WARN` level. They are now all at `WARN` level, but with a new behavior: if connection failures continue without a successful retry for a certain amount of time, the SDK will log a special `ERROR`-level message to warn you that this is not just a brief outage. The amount of time is one minute by default, but can be changed with the new `logDataSourceOutageAsErrorAfter` option in `LoggingConfigurationBuilder`. ([#190](https://github.com/launchdarkly/java-server-sdk/issues/190))
- Many internal methods have been rewritten to reduce the number of heap allocations in general.
- Evaluation of rules involving regex matches, date/time values, and semantic versions, has been speeded up by pre-parsing the values in the rules.
- Evaluation of rules involving an equality match to multiple values (such as &#34;name is one of X, Y, Z&#34;) has been speeded up by converting the list of values to a `Set`.
- The number of worker threads maintained by the SDK has been reduced so that most intermittent background tasks, such as listener notifications, event flush timers, and polling requests, are now dispatched on a single thread. The delivery of analytics events to LaunchDarkly still has its own thread pool because it is a heavier-weight task with greater need for concurrency.
- In polling mode, the poll requests previously ran on a dedicated worker thread that inherited its priority from the application thread that created the SDK. They are now on the SDK&#39;s main worker thread, which has `Thread.MIN_PRIORITY` by default (as all the other SDK threads already did) but the priority can be changed as described above.
- When using a persistent data store such as Redis, if there is a database outage, the SDK will wait until the end of the outage and then restart the stream connection to ensure that it has the latest data. Previously, it would try to restart the connection immediately and continue restarting if the database was still not available, causing unnecessary overhead.
 
### Fixed:
- `LDClient.version()` previously could not be used if the SDK classes were not packaged in their original jar. It now works correctly regardless of deployment details.
 
### Removed:
- All types and methods that were deprecated as of Java SDK 4.13.0 have been removed. This includes many `LDConfig.Builder()` methods, which have been replaced by the modular configuration syntax that was already added in the 4.12.0 and 4.13.0 releases. See the [migration guide](https://docs.launchdarkly.com/sdk/server-side/java/migration-4-to-5) for details on how to update your configuration code if you were using the older syntax.
- The Redis integration is no longer built into the main SDK library. See: https://github.com/launchdarkly/java-server-sdk-redis
- The deprecated New Relic integration has been removed.

## [4.14.4] - 2020-09-28
### Fixed:
- Restored compatibility with Java 7. A transitive dependency that required Java 8 had accidentally been included, and the CI build did not detect this because the tests were being run in Java 8 even though the compiler target was 7. CI builds now verify that the SDK really can run in Java 7. This fix is only for 4.x; the 5.x SDK still does not support Java 7.
- Bumped OkHttp version to 3.12.12 to avoid a crash on Java 8u252.
- Removed an obsolete comment that said the `trackMetric` method was not yet supported by the LaunchDarkly service; it is.

## [4.14.3] - 2020-09-03
### Fixed:
- Bump SnakeYAML from 1.19 to 1.26 to address CVE-2017-18640. The SDK only parses YAML if the application has configured the SDK with a flag data file, so it&#39;s unlikely this CVE would affect SDK usage as it would require configuration and access to a local file.

## [4.14.2] - 2020-09-01
### Fixed:
- Updated the version of OkHttp contained within the SDK from 3.12.10 to 3.14.9, to address multiple [known issues](https://square.github.io/okhttp/changelog_3x/) including an incompatibility with OpenJDK 8.0.252 under some conditions. ([#204](https://github.com/launchdarkly/java-server-sdk/issues/204))

## [4.14.1] - 2020-08-04
### Fixed:
- Deserializing `LDUser` from JSON using Gson resulted in an object that had nulls in some fields where nulls were not expected, which could cause null pointer exceptions later. While there was no defined behavior for deserializing users in the 4.x SDK (it is supported in 5.0 and above), it was simple to fix. Results of deserializing with any other JSON framework are undefined. ([#199](https://github.com/launchdarkly/java-server-sdk/issues/199))

## [4.14.0] - 2020-05-13
### Added:
- `EventSender` interface and `EventsConfigurationBuilder.eventSender()` allow you to specify a custom implementation of how event data is sent. This is mainly to facilitate testing, but could also be used to store and forward event data.

### Fixed:
- Changed the Javadoc comments for the `LDClient` constructors to provide a better explanation of the client&#39;s initialization behavior.

## [4.13.0] - 2020-04-21
### Added:
- The new methods `Components.httpConfiguration()` and `LDConfig.Builder.http()`, and the new class `HttpConfigurationBuilder`, provide a subcomponent configuration model that groups together HTTP-related options such as `connectTimeoutMillis` and `proxyHost` - similar to how `Components.streamingDataSource()` works for streaming-related options or `Components.sendEvents()` for event-related options. The individual `LDConfig.Builder` methods for those options will still work, but are deprecated and will be removed in version 5.0.
- `EvaluationReason` now has getter methods like `getRuleIndex()` that were previously only on specific reason subclasses. The subclasses will be removed in version 5.0.

### Changed:
- In streaming mode, the SDK will now drop and restart the stream connection if either 1. it receives malformed data (indicating that some data may have been lost before reaching the application) or 2. you are using a database integration (a persistent feature store) and a database error happens while trying to store the received data. In both cases, the intention is to make sure updates from LaunchDarkly are not lost; restarting the connection causes LaunchDarkly to re-send the entire flag data set. This makes the Java SDK&#39;s behavior consistent with other LaunchDarkly server-side SDKs.

(Note that this means if there is a sustained database outage, you may see repeated reconnections as the SDK receives the data from LaunchDarkly again, tries to store it again, and gets another database error. Starting in version 5.0, there will be a more efficient mechanism in which the stream will only be restarted once the database becomes available again; that is not possible in this version because of limitations in the feature store interface.)

### Fixed:
- Network errors during analytics event delivery could cause an unwanted large exception stacktrace to appear as part of the log message. This has been fixed to be consistent with the SDK&#39;s error handling in general: a brief message is logged at `ERROR` or `WARN` level, and the stacktrace only appears if you have enabled `DEBUG` level.

### Deprecated:
- `LDConfig.Builder` methods `connectTimeout`, `connectTimeoutMillis`, `proxyHost`, `proxyPort`, `proxyUsername`, `proxyPassword`, `sslSocketFactory`, `wrapperName`, and `wrapperVersion`. Use `LDConfig.Builder.http()` and `Components.httpConfiguration()` instead.
- `EvaluationReason` subclasses. Use the property getter methods on `EvaluationReason` instead.
- The built-in New Relic integration will be removed in the 5.0 release. Application code is not affected by this change since the integration was entirely reflection-based and was not exposed in the public API.

## [4.12.1] - 2020-03-20
### Changed:
- Improved the performance of the in-memory flag data store by using an immutable map that is atomically replaced on updates, so reads do not need a lock.
- Improved the performance of flag evaluations when there is a very long user target list in a feature flag or user segment, by representing the user key collection as a Set rather than a List.
- Updated OkHttp version to 3.12.10 (the latest version that still supports Java 7).


## [4.12.0] - 2020-01-30
The primary purpose of this release is to introduce newer APIs for the existing SDK features, corresponding to how they will work in the upcoming 5.0 release. The corresponding older APIs are now deprecated; switching from them to the newer ones now will facilitate migrating to 5.0 in the future. See below for details.

This release also adds diagnostic reporting as described below.

Note: if you are using the LaunchDarkly Relay Proxy to forward events, update the Relay to version 5.10.0 or later before updating to this Java SDK version.

### Added:
- The SDK now periodically sends diagnostic data to LaunchDarkly, describing the version and configuration of the SDK, the architecture and version of the runtime platform, and performance statistics. No credentials, hostnames, or other identifiable values are included. This behavior can be disabled with `LDConfig.Builder.diagnosticOptOut()` or configured with `EventProcessorBuilder.diagnosticRecordingInterval()`.
- Previously, most configuration options were set by setter methods in `LDConfig.Builder`. These are being superseded by builders that are specific to one area of functionality: for instance, `Components.streamingDataSource()` and `Components.pollingDataSource()` provide builders/factories that have options specific to streaming or polling, and the SDK's many options related to analytics events are now in a builder returned by `Components.sendEvents()`. Using this newer API makes it clearer which options are for what, and makes it impossible to write contradictory configurations like `.stream(true).pollingIntervalMillis(30000)`.
- The component "feature store" will be renamed to "data store". The interface for this is still called `FeatureStore` for backward compatibility, but `LDConfig.Builder` now has a `dataStore` method.
- There is a new API for specifying a _persistent_ data store (usually a database integration). This is now done using the new method `Components.persistentDataStore` and one of the new integration factories in the new package `com.launchdarkly.client.integrations`. The `Redis` class in that package provides the Redis integration; the next releases of the Consul and DynamoDB integrations will use the same semantics.
- The component "update processor" will be renamed to "data source". Applications normally do not need to use this interface except for the "file data source" testing component; the new entry point for this is `FileData` in `com.launchdarkly.client.integrations`.
- It is now possible to specify an infinite cache TTL for persistent feature stores by setting the TTL to a negative number, in which case the persistent store will never be read unless the application restarts. Use this mode with caution as described in the comment for `PersistentDataStoreBuilder.cacheForever()`.
- New `LDConfig.Builder` setters `wrapperName()` and `wrapperVersion()` allow a library that uses the Java SDK to identify itself for usage data if desired.

### Fixed:
- The Redis integration could fail to connect to Redis if the application did not explicitly specify a Redis URI. This has been fixed so it will default to `redis://localhost:6379` as documented.
- The `getCacheStats()` method on the deprecated `RedisFeatureStore` class was not working (the statistics were always zero). Note that in the newer persistent store API added in this version, there is now a different way to get cache statistics.

### Deprecated:
- Many `LDConfig.Builder` methods: see notes under "Added", and the per-method notes in Javadoc.
- `RedisFeatureStore` and `RedisFeatureStoreBuilder` in `com.launchdarkly.client`: see `Redis` in `com.launchdarkly.client.integrations`.
- `FileComponents` in `com.launchdarkly.client.files`: see `FileData` in `com.launchdarkly.client.integrations`.
- `FeatureStoreCacheConfig`: see `PersistentDataStoreBuilder`.


## [4.11.1] - 2020-01-17
### Fixed:
- Flag evaluation would fail (with a NullPointerException that would be logged, but not thrown to the caller) if a flag rule used a semantic version operator and the specified user attribute did not have a string value.
- The recently-added exception property of `EvaluationReason.Error` should not be serialized to JSON when sending reasons in analytics events, since the LaunchDarkly events service does not process that field and the serialization of an exception can be lengthy. The property is only meant for programmatic use.
- The SDK now specifies a uniquely identifiable request header when sending events to LaunchDarkly to ensure that events are only processed once, even if the SDK sends them two times due to a failed initial attempt. _(An earlier release note incorrectly stated that this behavior was added in 4.11.0. It is new in this release.)_

## [4.11.0] - 2020-01-16
### Added:
- When an `EvaluationReason` indicates that flag evaluation failed due to an unexpected exception (`getKind()` is `ERROR`, and `EvaluationReason.Error.getErrorKind()` is `EXCEPTION`), you can now examine the underlying exception via `EvaluationReason.Error.getException()`. ([#180](https://github.com/launchdarkly/java-server-sdk/issues/180))

## [4.10.1] - 2020-01-06
### Fixed:
- The `pom.xml` dependencies were incorrectly specifying `runtime` scope rather than `compile`, causing problems for applications that did not have their own dependencies on Gson and SLF4J. ([#151](https://github.com/launchdarkly/java-client/issues/151))

## [4.10.0] - 2019-12-13
### Added:
- Method overloads in `ArrayBuilder`/`ObjectBuilder` to allow easily adding values as booleans, strings, etc. rather than converting them to `LDValue` first.

### Changed:
- The SDK now generates fewer ephemeral objects on the heap from flag evaluations, by reusing `EvaluationReason` instances that have the same properties.

### Fixed:
- In rare circumstances (depending on the exact data in the flag configuration, the flag's salt value, and the user properties), a percentage rollout could fail and return a default value, logging the error "Data inconsistency in feature flag ... variation/rollout object with no variation or rollout". This would happen if the user's hashed value fell exactly at the end of the last "bucket" (the last variation defined in the rollout). This has been fixed so that the user will get the last variation.

### Deprecated:
- Deprecated `LDCountryCode`, `LDUser.Builder.country(LDCountryCode)`, and `LDUser.Builder.privateCountry(LDCountryCode)`. `LDCountryCode` will be removed in the next major release, for setting the `country` user property, applications should use `LDUser.Builder.country(String)` and `LDUser.Builder.privateCountry(String)` instead.
- `SegmentRule` is an internal implementation class that was accidentally made public.
- `NullUpdateProcessor` should not be referenced directly and will be non-public in the future; use the factory methods in `Components` instead.


## [4.9.1] - 2019-11-20
### Changed:
- Improved memory usage and performance when processing analytics events: the SDK now encodes event data to JSON directly, instead of creating intermediate objects and serializing them via reflection.

### Fixed:
- A bug introduced in version 4.9.0 was causing event delivery to fail if a user was created with the `User(string)` constructor, instead of the builder pattern.


## [4.9.0] - 2019-10-18
This release adds the `LDValue` class (in `com.launchdarkly.client.value`), which is a new abstraction for all of the data types supported by the LaunchDarkly platform. Since those are the same as the JSON data types, the SDK previously used the Gson classes `JsonElement`, `JsonObject`, etc. to represent them. This caused two problems: the public APIs are dependent on Gson, and the Gson object and array types are mutable so it was possible to accidentally modify values that are being used elsewhere in the SDK.

While the SDK still uses Gson internally, all references to Gson types in the API are now deprecated in favor of equivalent APIs that use `LDValue`. Developers are encouraged to migrate toward these as soon as possible; the Gson classes will be removed from the API in a future major version. If you are only using primitive types (boolean, string, etc.) for your feature flags and user attributes, then no changes are required.

There are no other changes in this release.

### Added:
- `LDValue` (see above).
- The new `jsonValueVariation` and `jsonValueVariationDetail` methods in `LDClient`/`LDClientInterface` are equivalent to `JsonVariation` and `JsonVariationDetail`, but use `LDValue`.

### Deprecated:
- In `LDClient`/`LDClientInterface`: `jsonVariation`/`jsonVariationDetail`. Use `jsonValueVariation`/`jsonValueVariationDetail`.
- In `LDClient`/`LDClientInterface`: `track(String, LDUser, JsonElement)` and `track(String, LDUser, JsonElement, double)`. Use `trackData(String, LDUser, LDValue)` and `trackMetric(String, LDUser, LDValue, double)`. The names are different to avoid compile-time ambiguity since both `JsonElement` and `LDValue` are nullable types.
- In `LDUserBuilder`: `custom(String, JsonElement)` and `privateCustom(String, JsonElement)`. Use the `LDValue` overloads.
- In `LDValue`: `fromJsonElement`, `unsafeFromJsonElement`, `asJsonElement`, `asUnsafeJsonElement`. These are provided for compatibility with code that still uses `JsonElement`, but will be removed in a future major version.


## [4.8.1] - 2019-10-17
### Fixed:
- The NewRelic integration was broken when using the default uberjar distribution, because the SDK was calling `Class.forName()` for a class name that was accidentally transformed by the Shadow plugin for Gradle. ([#171](https://github.com/launchdarkly/java-server-sdk/issues/171))
- Streaming connections were not using the proxy settings specified by `LDConfig.Builder.proxy()` and `LDConfig.Builder.proxyAuthenticator()`. ([#172](https://github.com/launchdarkly/java-server-sdk/issues/172))
- The SDK was creating an unused `OkHttpClient` instance as part of the static `LDConfig` instance used by the `LDClient(String)` constructor. This has been removed.
- Passing a null `sdkKey` or `config` to the `LDClient` constructors would always throw a `NullPointerException`, but it did not have a descriptive message. These exceptions now explain which parameter was null.

## [4.8.0] - 2019-09-30
### Added:
- Added support for upcoming LaunchDarkly experimentation features. See `LDClient.track(String, LDUser, JsonElement, double)`.

### Changed:
- Updated documentation comment for `intVariation` to clarify the existing rounding behavior for floating-point values: they are rounded toward zero.

## [4.7.1] - 2019-08-19
### Fixed:
- Fixed a race condition that could cause a `NumberFormatException` to be logged when delivering event data to LaunchDarkly (although the exception did not prevent the events from being delivered).

## [4.7.0] - 2019-08-02
### Added:
- In `RedisFeatureStoreBuilder`, the new methods `database`, `password`, and `tls` allow you to specify the database number, an optional password, and whether to make a secure connection to Redis. This is an alternative to specifying them as part of the Redis URI, e.g. `rediss://:PASSWORD@host:port/NUMBER`, which is also supported (previously, the database and password were supported in the URI, but the secure `rediss:` scheme was not).
- `LDConfig.Builder.sslSocketFactory` allows you to specify a custom socket factory and truststore for all HTTPS connections made by the SDK. This is for unusual cases where your Java environment does not have the proper root CA certificates to validate LaunchDarkly's certificate, or you are connecting through a secure proxy that has a self-signed certificate, and you do not want to modify Java's global truststore.

### Deprecated:
- `LDConfig.Builder.samplingInterval` is now deprecated. The intended use case for the `samplingInterval` feature was to reduce analytics event network usage in high-traffic applications. This feature is being deprecated in favor of summary counters, which are meant to track all events.

## [4.6.6] - 2019-07-10
### Fixed:
- Under conditions where analytics events are being generated at an extremely high rate (for instance, if an application is evaluating a flag repeatedly in a tight loop on many threads), a thread could be blocked indefinitely within the `Variation` methods while waiting for the internal event processing logic to catch up with the backlog. The logic has been changed to drop events if necessary so threads will not be blocked (similar to how the SDK already drops events if the size of the event buffer is exceeded). If that happens, this warning message will be logged once: "Events are being produced faster than they can be processed; some events will be dropped". Under normal conditions this should never happen; this change is meant to avoid a concurrency bottleneck in applications that are already so busy that thread starvation is likely.

## [4.6.5] - 2019-05-21
### Fixed
- The `LDConfig.Builder` method `userKeysFlushInterval` was mistakenly setting the value of `flushInterval` instead. (Thanks, [kutsal](https://github.com/launchdarkly/java-server-sdk/pull/163)!)

### Added
- CI tests now run against Java 8, 9, 10, and 11.

## [4.6.4] - 2019-05-01
### Changed
- Changed the artifact name from `com.launchdarkly:launchdarkly-client` to `com.launchdarkly:launchdarkly-java-server-sdk`
- Changed repository references to use the new URL

There are no other changes in this release. Substituting `launchdarkly-client` version 4.6.3 with `launchdarkly-java-server-sdk` version 4.6.4 will not affect functionality.

## [4.6.3] - 2019-03-21
### Fixed
- The SDK uberjars contained some JSR305 annotation classes such as `javax.annotation.Nullable`. These have been removed. They were not being used in the public API anyway. ([#156](https://github.com/launchdarkly/java-server-sdk/issues/156))
- If `track` or `identify` is called without a user, the SDK now logs a warning, and does not send an analytics event to LaunchDarkly (since it would not be processed without a user).
### Note on future releases

The LaunchDarkly SDK repositories are being renamed for consistency. This repository is now `java-server-sdk` rather than `java-client`.

The artifact names will also change. In the 4.6.3 release, the generated artifact was named `com.launchdarkly.client:launchdarkly-client`; in all future releases, it will be `com.launchdarkly.client:launchdarkly-java-server-sdk`.

## [4.6.2] - 2019-02-21
### Fixed
- If an unrecoverable `java.lang.Error` is thrown within the analytics event dispatching thread, the SDK will now log the error stacktrace to the configured logger and then disable event sending, so that all further events are simply discarded. Previously, the SDK could be left in a state where application threads would continue trying to push events onto a queue that was no longer being consumed, which could block those threads. The SDK will not attempt to restart the event thread after such a failure, because an `Error` typically indicates a serious problem with the application environment.
- Summary event counters now use 64-bit integers instead of 32-bit, so they will not overflow if there is an extremely large volume of events.
- The SDK's CI test suite now includes running the tests in Windows.

## [4.6.1] - 2019-01-14
### Fixed
- Fixed a potential race condition that could happen when using a DynamoDB or Consul feature store. The Redis feature store was not affected.


## [4.6.0] - 2018-12-12
### Added:
- The SDK jars now contain OSGi manifests which should make it possible to use them as bundles. The default jar requires Gson and SLF4J to be provided by other bundles, while the jar with the "all" classifier contains versions of Gson and SLF4J which it both exports and imports (i.e. it self-wires them, so it will use a higher version if you provide one). The "thin" jar is not recommended in an OSGi environment because it requires many dependencies which may not be available as bundles.
- There are now helper classes that make it much simpler to write a custom `FeatureStore` implementation. See the `com.launchdarkly.client.utils` package. The Redis feature store has been revised to use this code, although its functionality is unchanged except for the fix mentioned below.
- `FeatureStore` caching parameters (for Redis or other databases) are now encapsulated in the `FeatureStoreCacheConfig` class.

### Changed:
- The exponential backoff behavior when a stream connection fails has changed as follows. Previously, the backoff delay would increase for each attempt if the connection could not be made at all, or if a read timeout happened; but if a connection was made and then an error (other than a timeout) occurred, the delay would be reset to the minimum value. Now, the delay is only reset if a stream connection is made and remains open for at least a minute.

### Fixed:
- The Redis feature store would incorrectly report that it had not been initialized, if there happened to be no feature flags in your environment at the time that it was initialized.

### Deprecated:
- The `RedisFeatureStoreBuilder` methods `cacheTime`, `refreshStaleValues`, and `asyncRefresh` are deprecated in favor of the new `caching` method which sets these all at once.

## [4.5.1] - 2018-11-21
### Fixed:
- Fixed a build error that caused the `com.launchdarkly.client.files` package (the test file data source component added in v4.5.0) to be inaccessible unless you were using the "thin" jar.
- Stream connection errors are now logged at `WARN` level, rather than `ERROR`.

## [4.5.0] - 2018-10-26
### Added:
It is now possible to inject feature flags into the client from local JSON or YAML files, replacing the normal LaunchDarkly connection. This would typically be for testing purposes. See `com.launchdarkly.client.files.FileComponents`.

## [4.4.1] - 2018-10-15
### Fixed:
- The SDK's Maven releases had a `pom.xml` that mistakenly referenced dependencies that are actually bundled (with shading) inside of our jar, resulting in those dependencies being redundantly downloaded and included (without shading) in the runtime classpath, which could cause conflicts. This has been fixed. ([#122](https://github.com/launchdarkly/java-server-sdk/issues/122))

## [4.4.0] - 2018-10-01
### Added:
- The `allFlagsState()` method now accepts a new option, `FlagsStateOption.DETAILS_ONLY_FOR_TRACKED_FLAGS`, which reduces the size of the JSON representation of the flag state by omitting some metadata. Specifically, it omits any data that is normally used for generating detailed evaluation events if a flag does not have event tracking or debugging turned on.

### Fixed:
- JSON data from `allFlagsState()` is now slightly smaller even if you do not use the new option described above, because it completely omits the flag property for event tracking unless that property is `true`.

## [4.3.2] - 2018-09-11
### Fixed:
- Event delivery now works correctly when the events are being forwarded through a [LaunchDarkly Relay Proxy](https://github.com/launchdarkly/ld-relay).


## [4.3.1] - 2018-09-04
### Fixed:
- When evaluating a prerequisite feature flag, the analytics event for the evaluation did not include the result value if the prerequisite flag was off.
- The default Gson serialization for `LDUser` now includes all user properties. Previously, it omitted `privateAttributeNames`.

## [4.3.0] - 2018-08-27
### Added:
- The new `LDClient` method `allFlagsState()` should be used instead of `allFlags()` if you are passing flag data to the front end for use with the JavaScript SDK. It preserves some flag metadata that the front end requires in order to send analytics events correctly. Versions 2.5.0 and above of the JavaScript SDK are able to use this metadata, but the output of `allFlagsState()` will still work with older versions.
- The `allFlagsState()` method also allows you to select only client-side-enabled flags to pass to the front end, by using the option `FlagsStateOption.CLIENT_SIDE_ONLY`. ([#112](https://github.com/launchdarkly/java-server-sdk/issues/112))
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
- The new user builder methods `customValues` and `privateCustomValues` allow you to add a custom user attribute with multiple JSON values of mixed types. ([#126](https://github.com/launchdarkly/java-server-sdk/issues/126))
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
- Improved performance when evaluating flags with custom attributes, by avoiding an unnecessary caught exception (thanks, [rbalamohan](https://github.com/launchdarkly/java-server-sdk/issues/113)).


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
- Improved performance when evaluating flags with custom attributes, by avoiding an unnecessary caught exception (thanks, [rbalamohan](https://github.com/launchdarkly/java-server-sdk/issues/113)).


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
- [#92](https://github.com/launchdarkly/java-server-sdk/issues/92) Regex `matches` targeting rules now include the user if
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
- StreamingProcessor now supports increasing retry delays with jitter. Addresses [https://github.com/launchdarkly/java-server-sdk/issues/74[(https://github.com/launchdarkly/java-server-sdk/issues/74)

## [2.0.2] - 2016-09-13
### Added
- Now publishing artifact with 'all' classifier that includes SLF4J for ColdFusion or other systems that need it.

## [2.0.1] - 2016-08-12
### Removed
- Removed slf4j from default artifact: [#71](https://github.com/launchdarkly/java-server-sdk/issues/71)

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
