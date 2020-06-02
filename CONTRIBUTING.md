# Contributing to the LaunchDarkly Server-side SDK for Java
 
LaunchDarkly has published an [SDK contributor's guide](https://docs.launchdarkly.com/docs/sdk-contributors-guide) that provides a detailed explanation of how our SDKs work. See below for additional information on how to contribute to this SDK.
 
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

### Benchmarks

The project in the `benchmarks` subdirectory uses [JMH](https://openjdk.java.net/projects/code-tools/jmh/) to generate performance metrics for the SDK. This is run as a CI job, and can also be run manually by running `make` within `benchmarks` and then inspecting `build/reports/jmh`.

## Coding best practices

### Logging

Currently the SDK uses SLF4J for all log output. Here some things to keep in mind for good logging behavior:

1. Stick to the standardized logger name scheme defined in `Loggers.java`, preferably for all log output, but definitely for all log output above `DEBUG` level. Logger names can be useful for filtering log output, so it is desirable for users to be able to reference a clear, stable logger name like `com.launchdarkly.sdk.server.LDClient.Events` rather than a class name like `com.launchdarkly.sdk.server.EventSummarizer` which is an implementation detail. The text of a log message should be distinctive enough that we can easily find which class generated the message.

2. Use parameterized messages (`Logger.MAIN.info("The value is {}", someValue)`) rather than string concatenation (`Logger.MAIN.info("The value is " + someValue)`). This avoids the overhead of string concatenation if the logger is not enabled for that level. If computing the value is an expensive operation, and it is _only_ relevant for logging, consider implementing that computation via a custom `toString()` method on some wrapper type so that it will be done lazily only if the log level is enabled.

3. Exception stacktraces should only be logged at debug level. For instance: `Logger.MAIN.warn("An error happened: {}", ex.toString()); Logger.MAIN.debug(ex.toString(), ex)`. Also, consider whether the stacktrace would be at all meaningful in this particular context; for instance, in a `try` block around a network I/O operation, the stacktrace would only tell us (a) some internal location in Java standard libraries and (b) the location in our own code where we tried to do the operation; (a) is very unlikely to tell us anything that the exception's type and message doesn't already tell us, and (b) could be more clearly communicated by just writing a specific log message.

### Code coverage

It is important to keep unit test coverage as close to 100% as possible in this project. You can view the latest code coverage report in CircleCI, as `coverage/html/index.html` in the artifacts for the "Java 11 - Linux - OpenJDK" job. You can also run the report locally with `./gradlew jacocoTestCoverage` and view `./build/reports/jacoco/test`.

Sometimes a gap in coverage is unavoidable, usually because the compiler requires us to provide a code path for some condition that in practice can't happen and can't be tested, or because of a known issue with the code coverage tool. In all such cases, please mark the code with an explanatory comment beginning with "COVERAGE:".
