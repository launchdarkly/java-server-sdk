Contributing to the LaunchDarkly Server-side SDK for Java
================================================
 
LaunchDarkly has published an [SDK contributor's guide](https://docs.launchdarkly.com/docs/sdk-contributors-guide) that provides a detailed explanation of how our SDKs work. See below for additional information on how to contribute to this SDK.
 
## Submitting bug reports and feature requests
 
The LaunchDarkly SDK team monitors the [issue tracker](https://github.com/launchdarkly/java-server-sdk/issues) in the SDK repository. Bug reports and feature requests specific to this SDK should be filed in this issue tracker. The SDK team will respond to all newly filed issues within two business days.
 
## Submitting pull requests
 
We encourage pull requests and other contributions from the community. Before submitting pull requests, ensure that all temporary or unintended code is removed. Don't worry about adding reviewers to the pull request; the LaunchDarkly SDK team will add themselves. The SDK team will acknowledge all pull requests within two business days.
 
## Build instructions
 
### Prerequisites
 
The SDK builds with [Gradle](https://gradle.org/) and should be built against Java 7.
 
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

By default, the full unit test suite includes live tests of the Redis integration. Those tests expect you to have Redis running locally. To skip them, set the environment variable `LD_SKIP_DATABASE_TESTS=1` before running the tests.

## Benchmarks

The project in the `benchmarks` subdirectory uses [JMH](https://openjdk.java.net/projects/code-tools/jmh/) to generate performance metrics for the SDK. This is run as a CI job, and can also be run manually by running `make` within `benchmarks` and then inspecting `build/reports/jmh`.
