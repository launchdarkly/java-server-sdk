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

## Code coverage

It is important to keep unit test coverage as close to 100% as possible in this project.

Sometimes a gap in coverage is unavoidable, usually because the compiler requires us to provide a code path for some condition that in practice can't happen and can't be tested, or because of a known issue with the code coverage tool. Please handle all such cases as follows:

* Mark the code with an explanatory comment beginning with "COVERAGE:".

The current coverage report can be observed by running `./gradlew jacocoTestReport` and viewing `build/reports/jacoco/test/html/index.html`. This report is also produced as an artifact of the CircleCI build for the most recent Java version.
