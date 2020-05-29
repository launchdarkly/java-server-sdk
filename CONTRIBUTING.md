Contributing to the LaunchDarkly Server-side SDK for Java
================================================
 
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

## Code coverage

It is important to keep unit test coverage as close to 100% as possible in this project. You can view the latest code coverage report in CircleCI, as `coverage/html/index.html` in the artifacts for the "Java 11 - Linux - OpenJDK" job. You can also run the report locally with `./gradlew jacocoTestCoverage` and view `./build/reports/jacoco/test`.

Sometimes a gap in coverage is unavoidable, usually because the compiler requires us to provide a code path for some condition that in practice can't happen and can't be tested, or because of a known issue with the code coverage tool. Please handle all such cases as follows:

* Mark the code with an explanatory comment beginning with "COVERAGE:".
* Run the code coverage task with `./gradlew jacocoTestCoverageVerification`. It should fail and indicate how many lines of missed coverage exist in the method you modified.
* Add an item in the `knownMissedLinesForMethods` map in `build.gradle` that specifies that number of missed lines for that method signature.
