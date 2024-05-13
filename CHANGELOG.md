# Change log

All notable changes to the LaunchDarkly Java SDK will be documented in this file. This project adheres to [Semantic Versioning](http://semver.org).

## [7.4.1] - 2024-05-13
### Added:
- Adds warning log if excessive start wait time is used.

### Fixed:
- Improved preprocessing allocations to reduce memory footprint in rare flag configurations.

## [7.4.0] - 2024-04-26
### Added:
- This release introduces a Hooks API. Hooks are collections of user-defined callbacks that are executed by the SDK at various points of interest. You can use them to augment the SDK with metrics or tracing.

## [7.3.0] - 2024-03-14
### Changed:
- Redact anonymous attributes within feature events
- Always inline contexts for feature events

## [7.2.6] - 2024-02-09
### Added:
- LDReactorClient to adapt LDClient to reactive streams.

## [7.1.1] - 2023-11-14
### Fixed:
- Fixes NPE when interacting with Context created by copying.  (Thanks, [
pedroafonsodias](https://github.com/launchdarkly/java-sdk-common/pull/15))

## [7.1.0] - 2023-11-02
### Added:
- Added an improved way of setting wrapper information for wrapper SDKs. This functionality is primarily intended for use by LaunchDarkly while developing wrapper SDKs.

## [7.0.0] - 2023-10-16
The latest version of this SDK supports the ability to manage migrations or modernizations, using migration flags. You might use this functionality if you are optimizing queries, upgrading to new tech stacks, migrating from one database to another, or other similar technology changes. Migration flags are part of LaunchDarkly's Early Access Program. This feature is available to all LaunchDarkly customers but may undergo additional changes before it is finalized.

For detailed information about this version, refer to the list below. For information on how to upgrade from the previous version, read the [migration guide](https://docs.launchdarkly.com/sdk/server-side/java/migration-6-to-7).

### Added:
- A new `Migration` type which provides an out-of-the-box configurable migration framework.
- For more advanced use cases, added new `migrationVariation` and `trackMigration` methods on LDClient.

### Removed:
- Remove support for `LDUser` in `LDClient` methods. The `LDContext.fromUser` method can be used to convert an `LDUser` to an `LDContext`. In a future version it may be removed.

## [6.2.1] - 2023-06-29
### Changed:
- Bumping Guava version to incorporate CVE-2023-2976 fixes.

## [6.2.0] - 2023-06-13
### Added:
- Custom headers can now be added to all HTTP requests with `Components.httpConfiguration().addCustomHeader`.

## [6.1.0] - 2023-04-13
### Added:
- Support for Payload Filtering in streaming and polling modes. Payload Filtering is a beta feature that allows SDKs to download a subset of environment data, rather than full environments.

## [6.0.6] - 2023-03-20
### Fixed:
- Updated snakeyaml to v2.0.0 to address CVE-2022-1471. This vulnerability would only have affected applications that used the FileData feature with a YAML file, assuming an attacker had write access to the filesystem.

## [6.0.5] - 2023-02-01
### Fixed:
- Segment bug that returns the default value for variation if multiple flag rules refer to the same segment with a rule.

## [6.0.4] - 2023-01-10
### Fixed:
- If the stream connection failed when the SDK had only partially received a piece of JSON data from the stream, the SDK was sometimes logging a misleading error message about invalid JSON in addition to the normal error message about the connection failure.

## [5.10.7] - 2023-01-09
### Fixed:
- If the stream connection failed when the SDK had only partially received a piece of JSON data from the stream, the SDK was sometimes logging a misleading error message about invalid JSON in addition to the normal error message about the connection failure.

## [6.0.3] - 2023-01-06
### Fixed:
- Fixed unintended error behavior when the SDK is being shut down, if streaming is enabled. The symptom was that 1. the SDK could log a misleading message about a network error (in reality this was just the connection being deliberately closed) and 2. an uncaught exception could be thrown from the worker thread that managed that connection. The uncaught exception would be ignored in a default JVM configuration, but it could have more serious consequences in an application that had configured a default exception handler to be triggered by all uncaught exceptions.

## [5.10.6] - 2023-01-06
### Fixed:
- Fixed unintended error behavior when the SDK is being shut down, if streaming is enabled. The symptom was that 1. the SDK could log a misleading message about a network error (in reality this was just the connection being deliberately closed) and 2. an uncaught exception could be thrown from the worker thread that managed that connection. The uncaught exception would be ignored in a default JVM configuration, but it could have more serious consequences in an application that had configured a default exception handler to be triggered by all uncaught exceptions.

## [6.0.2] - 2023-01-04
### Fixed:
- Fixed vulnerability [CVE-2022-1471](https://nvd.nist.gov/vuln/detail/CVE-2022-1471) which could allow arbitrary code execution if using `FileDataSource` with a YAML file. (Thanks, [antonmos](https://github.com/launchdarkly/java-server-sdk/pull/289)!)

## [5.10.5] - 2023-01-04
### Fixed:
- Fixed vulnerability [CVE-2022-1471](https://nvd.nist.gov/vuln/detail/CVE-2022-1471) which could allow arbitrary code execution if using `FileDataSource` with a YAML file. (Thanks, [antonmos](https://github.com/launchdarkly/java-server-sdk/pull/289)!)

## [6.0.1] - 2022-12-20
### Changed:
- The internal implementation of the SSE client for streaming updates has been revised to use a single worker thread instead of two worker threads, reducing thread contention and memory usage.

## [5.10.4] - 2022-12-20
### Changed:
- The internal implementation of the SSE client for streaming updates has been revised to use a single worker thread instead of two worker threads, reducing thread contention and memory usage.

## [6.0.0] - 2022-12-07
The latest version of this SDK supports LaunchDarkly's new custom contexts feature. Contexts are an evolution of a previously-existing concept, "users." Contexts let you create targeting rules for feature flags based on a variety of different information, including attributes pertaining to users, organizations, devices, and more. You can even combine contexts to create "multi-contexts." 

For detailed information about this version, please refer to the list below. For information on how to upgrade from the previous version, please read the [migration guide](https://docs.launchdarkly.com/sdk/server-side/java/migration-5-to-6).

### Added:
- In `com.launchDarkly.sdk`, the types `LDContext` and `ContextKind` define the new context model.
- For all SDK methods that took an `LDUser` parameter, there is now an overload that takes an `LDContext`. The SDK still supports `LDUser` for now, but `LDContext` is the preferred model and `LDUser` may be removed in a future version.
- The `TestData` flag builder methods have been extended to support now context-related options, such as matching a key for a specific context type other than "user".

### Changed _(breaking changes from 6.x)_:
- It was previously allowable to set a user key to an empty string. In the new context model, the key is not allowed to be empty. Trying to use an empty key will cause evaluations to fail and return the default value.
- There is no longer such a thing as a `secondary` meta-attribute that affects percentage rollouts. If you set an attribute with that name in an `LDContext`, it will simply be a custom attribute like any other.
- The `anonymous` attribute in `LDUser` is now a simple boolean, with no distinction between a false state and a null state.
- Types such as `DataStore`, which define the low-level interfaces of LaunchDarkly SDK components and allow implementation of custom components, have been moved out of the `interfaces` subpackage into a new `subsystems` subpackage. Some types have been removed by using generics: for instance, the interface `DataSourceFactory` has been replaced by `ComponentConfigurer<DataSource>`. Application code normally does not refer to these types except possibly to hold a value for a configuration property such as `LDConfig.Builder.dataStore()`, so this change is likely to only affect configuration-related logic.

### Changed (requirements/dependencies/build):
- The SDK no longer has a dependency on [SLF4J](https://www.slf4j.org/). It will still use SLF4J as the default logging framework _if_ SLF4J is in the classpath, so it is the application's responsibility to declare its own dependency on SLF4J, as any application that uses SLF4J would normally do.
- Applications that use the database integrations for Redis, DynamoDB, or Consul must update to the latest major versions of the corresponding packages (`launchdarkly-java-server-sdk-redis-store`, etc.).

### Changed (behavioral changes):
- If SLF4J is not in the classpath, the SDK now uses `System.err` as its default logging destination. See "requirements/dependencies/build" above.
- The SDK can now evaluate segments that have rules referencing other segments.
- Analytics event data now uses a new JSON schema due to differences between the context model and the old user model.

### Removed:
- Removed all types, fields, and methods that were deprecated as of the most recent 5.x release.
- Removed the `secondary` meta-attribute in `LDUser` and `LDUser.Builder`.
- The `alias` method no longer exists because alias events are not needed in the new context model.
- The `inlineUsersInEvents` option no longer exists because it is not relevant in the new context model.

## [5.10.3] - 2022-10-20
### Fixed:
- The `pom.xml` specified a dependency on `com.launchdarkly:launchdarkly-logging` even though that library is already contained inside the SDK jar, which could cause extra copies of classes to be in the classpath. The dependency has been removed and the classes are still in the jar. ([#282](https://github.com/launchdarkly/java-server-sdk/issues/282))

## [5.10.2] - 2022-09-12
### Fixed:
- Updated `snakeyaml` to v1.32 to address [CVE-2022-38752](https://nvd.nist.gov/vuln/detail/CVE-2022-38752). This vulnerability would only have affected applications that used the `FileData` feature with a YAML file, assuming an attacker had write access to the filesystem.

## [5.10.1] - 2022-09-02
### Fixed:
- Updated `snakeyaml` dependency (used only if using `FileData` with YAML files) to v1.31 to address CVE-2022-25857 ([#275](https://github.com/launchdarkly/java-server-sdk/issues/275))
- Corrected documentation for default value of `LDConfig.Builder.startWait()`. (Thanks, [richardfearn](https://github.com/launchdarkly/java-server-sdk/pull/274)!)

## [5.10.0] - 2022-07-28
The main purpose of this release is to introduce a new logging facade, [`com.launchdarkly.logging`](https://github.com/launchdarkly/java-logging), to streamline how logging works in LaunchDarkly Java and Android code. Previously, the Java SDK always used SLF4J for logging; developers needed to provide an SLF4J configuration externally to specify the actual logging behavior. In this release, the default behavior is still to use SLF4J, but the logging facade can also be configured programmatically to do simple console logging without SLF4J, or to forward output to another framework such as `java.util.logging`, or to multiple destinations, or to capture output in memory. In a future major version release, the default behavior may be changed so that the SDK does not require SLF4J as a dependency.

### Added:
- In [`LoggingConfigurationBuilder`](https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/integrations/LoggingConfigurationBuilder.html), the new methods `adapter` and `level`, for the new logging capabilities mentioned above.
- `TestData.FlagBuilder.variationForAll` and `valueForAll`: new names for the deprecated methods listed below.

### Deprecated:
- `TestData.FlagBuilder.variationForAllUsers` and `valueForAllUsers`: These methods are being renamed because in the future, there will be other possible kinds of evaluation inputs that are not users, and these test methods will apply equally to those.

## [5.9.3] - 2022-07-28
### Changed:
- Updated `okhttp` dependency to version 4.9.3 to address a [reported vulnerability](https://security.snyk.io/vuln/SNYK-JAVA-COMSQUAREUPOKHTTP3-2958044) in earlier versions of that library, which could have allowed potentially sensitive information to be written to the log if you had put that information in a custom header value that contained an illegal character (see release notes for Java SDK [5.6.0](https://github.com/launchdarkly/java-server-sdk/releases/tag/5.6.0)). ([#271](https://github.com/launchdarkly/java-server-sdk/issues/271))

## [5.9.2] - 2022-07-20
### Changed:
- Further optimizations to reduce how many short-lived objects the SDK produces as a side effect of flag evaluations, causing less work for the garbage collector in applications that evaluate flags very frequently.

## [5.9.1] - 2022-06-30
### Changed:
- The SDK now uses memory more efficiently when parsing JSON flag/segment configuration data that it receives from LaunchDarkly, so there will be a less sizable transient memory usage spike if the flag/segment data is very large. This does not affect the baseline memory requirements for storing the data after it is received.
- The SDK now produces fewer short-lived objects as a side effect of flag evaluations, causing less work for the garbage collector in applications that evaluate flags very frequently.

## [5.9.0] - 2022-05-26
### Added:
- `LDConfig.Builder.serviceEndpoints` provides a simpler way of setting custom service base URIs, if you are connecting to a LaunchDarkly Relay Proxy instance, a private LaunchDarkly instance, or a test fixture. Previously, this required setting a BaseURI property for each individual service (streaming, events, etc.). If using the Relay Proxy, simply remove any BaseURI calls in your SDK configuration and call `serviceEndpoints(Components.serviceEndpoints().relayProxy(myRelayProxyUri))` on the configuration builder.

### Fixed:
- Fixed documentation comments for the variation methods to clarify that `defaultValue` is used if there is an error fetching the variation or the flag doesn't exist, not when the flag is disabled.

## [5.8.1] - 2022-05-04
### Fixed:
- Calling `stringVariationDetail` with a flag whose variations are _not_ strings, and passing `null` as the default value parameter, would result in an `EvaluationDetail` that had a null value but had a regular evaluation reason and variation index (whatever those would be for a successful evaluation of that flag). It now correctly returns a `WRONG_TYPE` error reason, and `NO_VARIATION` for the variation index.
- If a field in `Config.ApplicationInfo` is set to a string longer than 64 characters, the SDK will now log a warning and discard it, since the LaunchDarkly services cannot process such strings for these fields.

## [5.8.0] - 2022-04-18
### Added:
- `LDConfig.Builder.applicationInfo()`, for configuration of application metadata that may be used in LaunchDarkly analytics or other product features. This does not affect feature flag evaluations.

## [5.7.1] - 2022-02-04
### Fixed:
- Fixed a packaging issue causing `launchdarkly-java-sdk-common` to be included as a dependency in the SDK's generated `pom` file. This introduces duplicate classes in the application's `jar` file. The duplicate classes can prevent the SDK's custom serialization logic from being used, due to not correctly referencing the shaded class names. ([#258](hhttps://github.com/launchdarkly/java-server-sdk/issues/258))

## [5.7.0] - 2022-01-28
### Added:
- The SDK now supports evaluation of Big Segments. An Early Access Program for creating and syncing Big Segments from customer data platforms is available to enterprise customers.

### Changed:
- CI builds now include a cross-platform test suite implemented in https://github.com/launchdarkly/sdk-test-harness. This covers many test cases that are also implemented in unit tests, but may be extended in the future to ensure consistent behavior across SDKs in other areas.

## [5.6.7] - 2022-01-28
### Fixed:
- When using `allFlagsState` to produce bootstrap data for the JavaScript SDK, the Java SDK was not returning the correct metadata for evaluations that involved an experiment. As a result, the analytics events produced by the JavaScript SDK did not correctly reflect experimentation results.
- In feature flag rules using the `before` and `after` date operators, if two ISO-8601 string values were compared that represented the exact same absolute date in different time zones (such as `2000-01-01T08:00:00Z` and `2000-01-01T00:00:00-08:00`), the SDK wrongly treated them as unequal. This did not affect strings that represented different absolute dates, which were always compared correctly. The SDK now handles both cases correctly.
- The `com.launchdarkly.sdk.json` serialization methods were sometimes omitting JSON object properties in cases where it would have been more correct to show the property with a `null` value. This mainly affected JSON data produced by `LDClient.allFlagsState()`, where the presence of a flag key with a `null` value would indicate that the flag existed but could not be evaluated due to an error, as opposed to the flag not existing.

## [5.6.6] - 2022-01-07
### Fixed:
- The SDK build process was accidentally including a `module-info.class` file in the jar that was from a different module (`jdk.zipfs`). This has been removed. The SDK does not currently have Java module metadata. ([#252](https://github.com/launchdarkly/java-server-sdk/issues/252))

## [5.6.5] - 2021-12-08
### Fixed:
- If it received an HTTP 401 or 403 error from LaunchDarkly, indicating that the SDK key was invalid, the SDK would still continue trying to send diagnostic events. ([#250](https://github.com/launchdarkly/java-server-sdk/issues/250))

## [5.6.4] - 2021-11-30
### Fixed:
- Updated Gson to 2.8.9 for a [security bugfix](https://github.com/google/gson/pull/1991).

## [5.6.3] - 2021-10-12
### Fixed:
- If Java's default locale was not US/English, the SDK would fail to parse dates in the standard RFC1123 format in HTTP responses. The symptoms were that the warning `Received invalid Date header from events service` would appear in logs, and event debugging might not stop at the correct time if the system clock was different from the LaunchDarkly services' clock (which is why the SDK checks the Date header).

## [5.6.2] - 2021-08-09
### Fixed:
- `FeatureFlagsStateBuilder.build()` is now public. The other builder methods were made public in v5.6.0, but were not useful because `build()` was still package-private.

## [5.6.1] - 2021-07-07
This release fixes two packaging errors that could produce unwanted Java dependency behavior, as described below. There are no changes to the SDK&#39;s functionality in this release, and you do not need to modify your code or your build.

### Fixed:
- Two Jackson packages (`com.fasterxml.jackson.core:jackson-core`, `com.fasterxml.jackson.core:jackson-databind`) were mistakenly listed as dependencies in the SDK&#39;s metadata, causing those packages to be downloaded and included in the classpath even if you were not using them. The SDK does not require Jackson, even though it can optionally be made to use it. This was meant to be fixed in the 5.5.0 release as previously described in the changelog, but was not.
- The SDK jar manifest contained a `Class-Path` attribute that referenced SLF4J and Jackson jars at a specific relative file path. This could cause a warning to be printed if those jars did not exist at that file path, even if they were elsewhere in your classpath. The `Class-Path` attribute is mainly useful for independently-deployable application jars and is not useful here; it has been removed. ([#240](https://github.com/launchdarkly/java-server-sdk/issues/240))

## [5.6.0] - 2021-07-02
### Added:
- The `builder()` method in `FeatureFlagsState`, for creating instances of that class (most likely useful in test code). ([#234](https://github.com/launchdarkly/java-server-sdk/issues/234))

### Fixed:
- If you called the `LDClient` constructor with an SDK key that contained a character less than `0x20` or greater than `0x7e`, it would throw an `IllegalArgumentException` that contained the full SDK key string in its message. Since the string might contain a real key (if for instance the application had read the SDK key from configuration data that included a newline character, and neglected to trim the newline), exposing the value in an exception message that might end up in a log was a security risk. This has been changed so that the exception message only says the key contains an invalid character, but does not include the value. (The underlying exception behavior is part of the OkHttp library, so be aware that if you inject any custom headers with illegal characters into your HTTP configuration, their values might still be exposed in this way.)
- In polling mode, the SDK would attempt to reconnect to the LaunchDarkly streaming service even if it received an HTTP 401 error. It should reconnect for other errors such as 503, but 401 indicates that the SDK key is invalid and a retry cannot succeed; the SDK did have logic to permanently stop the connection in this case, but it was not working. (This is equivalent to the bug that was fixed in 5.5.1, but for polling mode.)
- Fixed documentation comments for `FileData` to clarify that you should _not_ use `offline` mode in conjunction with `FileData`; instead, you should just turn off events if you don&#39;t want events to be sent. Turning on `offline` mode will disable `FileData` just as it disables all other data sources. ([#235](https://github.com/launchdarkly/java-server-sdk/issues/235))

## [5.5.1] - 2021-06-24
### Fixed:
- The SDK was attempting to reconnect to the LaunchDarkly streaming service even if it received an HTTP 401 error. It should reconnect for other errors such as 503, but 401 indicates that the SDK key is invalid and a retry cannot succeed; the SDK did have logic to permanently stop the connection in this case, but it was not working.

## [5.5.0] - 2021-06-17
### Added:
- The SDK now supports the ability to control the proportion of traffic allocation to an experiment. This works in conjunction with a new platform feature now available to early access customers.

### Fixed:
- Removed unnecessary dependencies on Jackson packages in `pom.xml`. The SDK does not require Jackson to be present, although it does provide convenience methods for interacting with Jackson if it is present.

## [5.4.1] - 2021-06-10
### Fixed:
- If a rule clause in a feature flag or user segment had a JSON `null` as a match value, the SDK would fail to parse the JSON data, causing an overall inability to receive flag data from LaunchDarkly as long as this condition existed. This is an abnormal condition since it is not possible to match any user attribute against a null value, but it is technically allowed by the JSON schema. The SDK will now correctly parse the data.

## [5.4.0] - 2021-04-22
### Added:
- Added the `alias` method to `LDClient`. This can be used to associate two user objects for analytics purposes with an alias event.
- In `com.launchdarkly.sdk.json.LDGson`, added convenience methods `valueToJsonElement` and `valueMapToJsonElementMap` for applications that use Gson types.
- In `com.launchdarkly.sdk.LDValue`, added convenience method `arrayOf()`.

### Changed:
- In `com.launchdarkly.sdk.json`, the implementations of `LDGson.typeAdapters` and `LDJackson.module` have been changed for better efficiency in deserialization. Instead of creating an intermediate string representation and re-parsing that, they now have a more direct way for the internal deserialization logic to interact with the streaming parser in the application's Gson or Jackson instance.

### Fixed:
- `Gson.toJsonTree` now works with LaunchDarkly types, as long as you have configured it as described in `com.launchdarkly.sdk.json.LDGson`. Previously, Gson was able to convert these types to and from JSON string data, but `toJsonTree` did not work due to a [known issue](https://github.com/google/gson/issues/1289) with the `JsonWriter.jsonValue` method; the SDK code no longer uses that method.
- `LDValue.parse()` now returns `LDValue.ofNull()` instead of an actual null reference if the JSON string is `null`.
- Similarly, when deserializing an `EvaluationDetail<LDValue>` from JSON, if the `value` property is `null`, it will now translate this into `LDValue.ofNull()` rather than an actual null reference.

## [5.3.1] - 2021-04-08
### Fixed:
- Updated the `commons-codec` dependency from 1.10 to 1.15. There was a [known vulnerability](https://github.com/apache/commons-codec/commit/48b615756d1d770091ea3322eefc08011ee8b113) in earlier versions of `commons-codec`-- although it did not affect this SDK, since it involved base64 decoding, which is not a thing the SDK ever does.

## [5.3.0] - 2021-03-09
### Added:
- When using the file data source, `FileDataSourceBuilder.duplicateKeysHandling` allows you to specify that duplicate flag keys should _not_ cause an error as they normally would. [(#226)](https://github.com/launchdarkly/java-server-sdk/issues/226)

## [5.2.3] - 2021-02-19
### Fixed:
- The flag update notification mechanism in `FlagTracker` did not work when the data source was `FileData`. This has been fixed so that whenever `FileData` reloads the data file(s) due to a file being modified, it signals that the flags were updated. The SDK will behave as if every flag was updated in this case, regardless of which part of the file data was actually changed, but it was already the case that a flag change event did not necessarily mean there was any _significant_ change to the flag. You can use `addFlagValueChangeListener` (as opposed to `addFlagChangeListener`) to be notified only of changes that affect a specific flag's value for a specific user. ([#224](https://github.com/launchdarkly/java-server-sdk/issues/224))

## [5.2.2] - 2021-01-15
### Fixed:
- Updated Guava from `28.2-jre` to `30.1-jre` to resolve [CVE-2020-8908](https://nvd.nist.gov/vuln/detail/CVE-2020-8908). This CVE did not affect the SDK as the SDK does not use the vulnerable functionality.

## [5.2.1] - 2020-12-01
### Fixed:
- `TestData.FlagBuilder` did not copy flags' targeting rules when applying an update to an existing test flag. ([#220](https://github.com/launchdarkly/java-server-sdk/issues/220))

## [5.2.0] - 2020-10-09
### Added:
- Add support for setting a `socketFactory` in the `HttpConfiguration` builder. This is used to create sockets when initiating HTTP connections. For TLS connections `sslSocketFactory` is used.

## [5.1.1] - 2020-09-30
### Fixed:
- The `com.launchdarkly.sdk.json.LDJackson` class was not usable in the default distribution of the SDK (nor in the `all` distribution) because Jackson class names had been incorrectly modified by the shading step in the build. ([#213](https://github.com/launchdarkly/java-server-sdk/issues/213))
- Setting custom base URIs for the streaming, polling, or events service could produce incorrect results if the URI had a context path. (Thanks, [msafari](https://github.com/launchdarkly/java-server-sdk/pull/212)!)
- Corrected format strings in some log messages. ([#211](https://github.com/launchdarkly/java-server-sdk/issues/211))

## [5.1.0] - 2020-09-04
### Added:
- The `TestData` class in `com.launchdarkly.sdk.server.integrations` is a new way to inject feature flag data programmatically into the SDK for testing—either with fixed values for each flag, or with targets and/or rules that can return different values for different users. Unlike `FileData`, this mechanism does not use any external resources, only the data that your test code has provided.

### Fixed:
- In polling mode, the log message "LaunchDarkly client initialized" was appearing after every successful poll request. It should only appear once.

## [5.0.5] - 2020-09-03
### Fixed:
- Bump SnakeYAML from 1.19 to 1.26 to address CVE-2017-18640. The SDK only parses YAML if the application has configured the SDK with a flag data file, so it's unlikely this CVE would affect SDK usage as it would require configuration and access to a local file.


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
- You can tell the SDK to notify you whenever a feature flag's configuration has changed (either in general, or in terms of its result for a specific user), using `LDClient.getFlagTracker()`. ([#83](https://github.com/launchdarkly/java-server-sdk/issues/83))
- You can monitor the status of the SDK's data source (which normally means the streaming connection to the LaunchDarkly service) with `LDClient.getDataSourceStatusProvider()`. This allows you to check the current connection status, and to be notified if this status changes. ([#184](https://github.com/launchdarkly/java-server-sdk/issues/184))
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
- The component interfaces `FeatureStore` and `UpdateProcessor` have been renamed to `DataStore` and `DataSource`. The factory interfaces for these components now receive SDK configuration options in a different way that does not expose other components' configurations to each other.
- The `PersistentDataStore` interface for creating your own database integrations has been simplified by moving all of the serialization and caching logic into the main SDK code.
 
### Changed (behavioral changes):
- SLF4J logging now uses a simpler, more stable set of logger names instead of using the names of specific implementation classes that are subject to change. General messages are logged under `com.launchdarkly.sdk.server.LDClient`, while messages about specific areas of functionality are logged under that name plus `.DataSource` (streaming, polling, file data, etc.), `.DataStore` (database integrations), `.Evaluation` (unexpected errors during flag evaluations), or `.Events` (analytics event processing).
- If analytics events are disabled with `Components.noEvents()`, the SDK now avoids generating any analytics event objects internally. Previously they were created and then discarded, causing unnecessary heap churn.
- Network failures and server errors for streaming or polling requests were previously logged at `ERROR` level in most cases but sometimes at `WARN` level. They are now all at `WARN` level, but with a new behavior: if connection failures continue without a successful retry for a certain amount of time, the SDK will log a special `ERROR`-level message to warn you that this is not just a brief outage. The amount of time is one minute by default, but can be changed with the new `logDataSourceOutageAsErrorAfter` option in `LoggingConfigurationBuilder`. ([#190](https://github.com/launchdarkly/java-server-sdk/issues/190))
- Many internal methods have been rewritten to reduce the number of heap allocations in general.
- Evaluation of rules involving regex matches, date/time values, and semantic versions, has been speeded up by pre-parsing the values in the rules.
- Evaluation of rules involving an equality match to multiple values (such as "name is one of X, Y, Z") has been speeded up by converting the list of values to a `Set`.
- The number of worker threads maintained by the SDK has been reduced so that most intermittent background tasks, such as listener notifications, event flush timers, and polling requests, are now dispatched on a single thread. The delivery of analytics events to LaunchDarkly still has its own thread pool because it is a heavier-weight task with greater need for concurrency.
- In polling mode, the poll requests previously ran on a dedicated worker thread that inherited its priority from the application thread that created the SDK. They are now on the SDK's main worker thread, which has `Thread.MIN_PRIORITY` by default (as all the other SDK threads already did) but the priority can be changed as described above.
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
- Bump SnakeYAML from 1.19 to 1.26 to address CVE-2017-18640. The SDK only parses YAML if the application has configured the SDK with a flag data file, so it's unlikely this CVE would affect SDK usage as it would require configuration and access to a local file.

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
- Changed the Javadoc comments for the `LDClient` constructors to provide a better explanation of the client's initialization behavior.

## [4.13.0] - 2020-04-21
### Added:
- The new methods `Components.httpConfiguration()` and `LDConfig.Builder.http()`, and the new class `HttpConfigurationBuilder`, provide a subcomponent configuration model that groups together HTTP-related options such as `connectTimeoutMillis` and `proxyHost` - similar to how `Components.streamingDataSource()` works for streaming-related options or `Components.sendEvents()` for event-related options. The individual `LDConfig.Builder` methods for those options will still work, but are deprecated and will be removed in version 5.0.
- `EvaluationReason` now has getter methods like `getRuleIndex()` that were previously only on specific reason subclasses. The subclasses will be removed in version 5.0.

### Changed:
- In streaming mode, the SDK will now drop and restart the stream connection if either 1. it receives malformed data (indicating that some data may have been lost before reaching the application) or 2. you are using a database integration (a persistent feature store) and a database error happens while trying to store the received data. In both cases, the intention is to make sure updates from LaunchDarkly are not lost; restarting the connection causes LaunchDarkly to re-send the entire flag data set. This makes the Java SDK's behavior consistent with other LaunchDarkly server-side SDKs.

(Note that this means if there is a sustained database outage, you may see repeated reconnections as the SDK receives the data from LaunchDarkly again, tries to store it again, and gets another database error. Starting in version 5.0, there will be a more efficient mechanism in which the stream will only be restarted once the database becomes available again; that is not possible in this version because of limitations in the feature store interface.)

### Fixed:
- Network errors during analytics event delivery could cause an unwanted large exception stacktrace to appear as part of the log message. This has been fixed to be consistent with the SDK's error handling in general: a brief message is logged at `ERROR` or `WARN` level, and the stacktrace only appears if you have enabled `DEBUG` level.

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
- To reduce the network bandwidth used for analytics events, feature request events are now sent as counters rather than individual events, and user details are now sent only at intervals rather than in each event. These behaviors can be modified through the LaunchDarkly UI and with the new configuration option `inlineUsersInEvents`.
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
- Support for specifying [private user attributes](https://docs.launchdarkly.com/home/users/attributes#creating-private-user-attributes) in order to prevent user attributes from being sent in analytics events back to LaunchDarkly. See the `allAttributesPrivate` and `privateAttributeNames` methods on `LDConfig.Builder` as well as the `privateX` methods on `LDUser.Builder`.

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
