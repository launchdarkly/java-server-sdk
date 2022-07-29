# Contributing to the LaunchDarkly Server-side SDK for Java
 
LaunchDarkly has published an [SDK contributor's guide](https://docs.launchdarkly.com/sdk/concepts/contributors-guide) that provides a detailed explanation of how our SDKs work. See below for additional information on how to contribute to this SDK.
 
## Submitting bug reports and feature requests
 
The LaunchDarkly SDK team monitors the [issue tracker](https://github.com/launchdarkly/java-server-sdk/issues) in the SDK repository. Bug reports and feature requests specific to this SDK should be filed in this issue tracker. The SDK team will respond to all newly filed issues within two business days.
 
## Submitting pull requests
 
We encourage pull requests and other contributions from the community. Before submitting pull requests, ensure that all temporary or unintended code is removed. Don't worry about adding reviewers to the pull request; the LaunchDarkly SDK team will add themselves. The SDK team will acknowledge all pull requests within two business days.
 
## Build instructions
 
### Prerequisites
 
The SDK builds with [Gradle](https://gradle.org/) and should be built against Java 8.

Many basic classes are implemented in the module `launchdarkly-java-sdk-common`, whose source code is in the [`launchdarkly/java-sdk-common`](https://github.com/launchdarkly/java-sdk-common) repository; this is so the common code can be shared with the LaunchDarkly Android SDK. By design, the LaunchDarkly Java SDK distribution does not expose a dependency on that module; instead, its classes and Javadoc content are embedded in the SDK jars.

### Building

To build the SDK without running any tests:
```
./gradlew jar
```

If you wish to clean your working directory between builds, you can clean it by running:
```
./gradlew clean
```

If you wish to use your generated SDK artifact by another Maven/Gradle project such as [hello-java](https://github.com/launchdarkly/hello-java), you will likely want to publish the artifact to your local Maven repository so that your other project can access it.
```
./gradlew publishToMavenLocal
```

### Testing
 
To build the SDK and run all unit tests:
```
./gradlew test
```

To run the SDK contract test suite in Linux (see [`contract-tests/README.md`](./contract-tests/README.md)):

```bash
make contract-tests
```

### Benchmarks

The project in the `benchmarks` subdirectory uses [JMH](https://openjdk.java.net/projects/code-tools/jmh/) to generate performance metrics for the SDK. This is run as a CI job, and can also be run manually by running `make` within `benchmarks` and then inspecting `build/reports/jmh`.

## Coding best practices

### Logging

The SDK uses a LaunchDarkly logging facade, [`com.launchdarkly.logging`](https://github.com/launchdarkly/java-logging). By default, this facade sends output to SLF4J.

Here some things to keep in mind for good logging behavior:

1. Stick to the standardized logger name scheme defined in `Loggers.java`, preferably for all log output, but definitely for all log output above `DEBUG` level. Logger names can be useful for filtering log output, so it is desirable for users to be able to reference a clear, stable logger name like `com.launchdarkly.sdk.server.LDClient.Events` rather than a class name like `com.launchdarkly.sdk.server.EventSummarizer` which is an implementation detail. The text of a log message should be distinctive enough that we can easily find which class generated the message.

2. Use parameterized messages (`logger.info("The value is {}", someValue)`) rather than string concatenation (`logger.info("The value is " + someValue)`). This avoids the overhead of string concatenation if the logger is not enabled for that level. If computing the value is an expensive operation, and it is _only_ relevant for logging, consider implementing that computation via a custom `toString()` method on some wrapper type so that it will be done lazily only if the log level is enabled.

3. There is a standard pattern for logging exceptions, using the `com.launchdarkly.logging.LogValues` helpers. First, log the basic description of the exception at whatever level is appropriate (`WARN` or `ERROR`): `logger.warn("An error happened: {}", LogValues.exceptionSummary(ex))`. Then, log a stack at debug level: `logger.debug(LogValues.exceptionTrace(ex))`. The `exceptionTrace` helper is lazily evaluated so that the stacktrace will only be computed if debug logging is actually enabled. However, consider whether the stacktrace would be at all meaningful in this particular context; for instance, in a `try` block around a network I/O operation, the stacktrace would only tell us (a) some internal location in Java standard libraries and (b) the location in our own code where we tried to do the operation; (a) is very unlikely to tell us anything that the exception's type and message doesn't already tell us, and (b) could be more clearly communicated by just writing a specific log message.

### Code coverage

It is important to keep unit test coverage as close to 100% as possible in this project. You can view the latest code coverage report in CircleCI, as `coverage/html/index.html` in the artifacts for the "Java 11 - Linux - OpenJDK" job. You can also run the report locally with `./gradlew jacocoTestCoverage` and view `./build/reports/jacoco/test`. _The CircleCI build will fail if you commit a change that increases the number of uncovered lines_, unless you explicitly add an override as shown below.

Sometimes a gap in coverage is unavoidable, usually because the compiler requires us to provide a code path for some condition that in practice can't happen and can't be tested, or because of a known issue with the code coverage tool. Please handle all such cases as follows:

* Mark the code with an explanatory comment beginning with "COVERAGE:".
* Run the code coverage task with `./gradlew jacocoTestCoverageVerification`. It should fail and indicate how many lines of missed coverage exist in the method you modified.
* Add an item in the `knownMissedLinesForMethods` map in `build.gradle` that specifies that number of missed lines for that method signature. For instance, if the method `com.launchdarkly.sdk.server.SomeClass.someMethod(java.lang.String)` has two missed lines that cannot be covered, you would add `"SomeClass.someMethod(java.lang.String)": 2`.
