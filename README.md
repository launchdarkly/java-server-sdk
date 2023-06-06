# LaunchDarkly Server-side SDK for Java

[![Circle CI](https://circleci.com/gh/launchdarkly/java-server-sdk.svg?style=shield)](https://circleci.com/gh/launchdarkly/java-server-sdk)
[![Javadocs](http://javadoc.io/badge/com.launchdarkly/launchdarkly-java-server-sdk.svg)](http://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk)

## LaunchDarkly overview

[LaunchDarkly](https://www.launchdarkly.com) is a feature management platform that serves trillions of feature flags daily to help teams build better software, faster. [Get started](https://docs.launchdarkly.com/home/getting-started) using LaunchDarkly today!
 
[![Twitter Follow](https://img.shields.io/twitter/follow/launchdarkly.svg?style=social&label=Follow&maxAge=2592000)](https://twitter.com/intent/follow?screen_name=launchdarkly)

## Supported Java versions

This version of the LaunchDarkly SDK works with Java 8 and above.

## Distributions

Two variants of the SDK jar are published to Maven:

* The default uberjar - this is accessible as `com.launchdarkly:launchdarkly-java-server-sdk:jar` and is the dependency used in the "[Getting started](https://docs.launchdarkly.com/sdk/server-side/java#getting-started)" section of the SDK reference guide as well as in the [`hello-java`](https://github.com/launchdarkly/hello-java) sample app. This variant contains the SDK classes and all of its required dependencies. All bundled dependencies that are not surfaced in the public API have shaded package names (and are not exported in OSGi), so they will not interfere with any other versions of the same packages.
* The "thin" jar - add `<classifier>thin</classifier>` in Maven, or `:thin` in Gradle. This contains only the SDK classes, without its dependencies. Applications using this jar must provide all of the dependencies that are in the SDK's `build.gradle`, so it is intended for use only in special cases.

Previous SDK versions also included a third classifier, `all`, which was the same as the default uberjar but also contained the SLF4J API. This no longer exists because the SDK no longer requires the SLF4J API to be in the classpath.

## Getting started

Refer to the [SDK reference guide](https://docs.launchdarkly.com/sdk/server-side/java#getting-started) for instructions on getting started with using the SDK.

## Logging

By default, the LaunchDarkly SDK uses [SLF4J](https://www.slf4j.org/) _if_ the SLF4J API is present in the classpath. SLF4J has its own configuration mechanisms for determining where output will go, and filtering by level and/or logger name.

If SLF4J is not in the classpath, the SDK's default logging destination is `System.err`.

The SDK can also be configured to use other adapters from the [com.launchdarkly.logging](https://github.com/launchdarkly/java-logging) facade. See `LoggingConfigurationBuilder`. This allows the logging behavior to be completely determined by the application, rather than by external SLF4J configuration.

For an example of using the default SLF4J behavior with a simple console logging configuration, check out the [`slf4j-logging` branch](https://github.com/launchdarkly/hello-java/tree/slf4j-logging) of the [`hello-java`](https://github.com/launchdarkly/hello-java) project. The [main branch](https://github.com/launchdarkly/hello-java) of `hello-java` uses console logging that is programmatically configured without SLF4J.

All loggers are namespaced under `com.launchdarkly`, if you are using name-based filtering.

Be aware of two considerations when enabling the DEBUG log level:
1. Debug-level logs can be very verbose. It is not recommended that you turn on debug logging in high-volume environments.
1. Potentially sensitive information is logged including LaunchDarkly users created by you in your usage of this SDK.

## Using flag data from a file

For testing purposes, the SDK can be made to read feature flag state from a file or files instead of connecting to LaunchDarkly. See <a href="https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/integrations/FileData.html">FileData</a> for more details.

## DNS caching issues

LaunchDarkly servers operate in a load-balancing framework which may cause their IP addresses to change. This could result in the SDK failing to connect to LaunchDarkly if an old IP address is still in your system's DNS cache.

Unlike some other languages, in Java the DNS caching behavior is controlled by the Java virtual machine rather than the operating system. The default behavior varies depending on whether there is a [security manager](https://docs.oracle.com/javase/tutorial/essential/environment/security.html): if there is, IP addresses will _never_ expire. In that case, we recommend that you set the security property `networkaddress.cache.ttl`, as described [here](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-jvm-ttl.html), to a number of seconds such as 30 or 60 (a lower value will reduce the chance of intermittent failures, but will slightly reduce networking performance).

## Learn more

Read our [documentation](https://docs.launchdarkly.com) for in-depth instructions on configuring and using LaunchDarkly. You can also head straight to the [complete reference guide for this SDK](https://docs.launchdarkly.com/docs/java-sdk-reference) or our [code-generated API documentation](https://launchdarkly.github.io/java-server-sdk/).

## Testing

We run integration tests for all our SDKs using a centralized test harness. This approach gives us the ability to test for consistency across SDKs, as well as test networking behavior in a long-running application. These tests cover each method in the SDK, and verify that event sending, flag evaluation, stream reconnection, and other aspects of the SDK all behave correctly.

## Contributing

We encourage pull requests and other contributions from the community. Check out our [contributing guidelines](CONTRIBUTING.md) for instructions on how to contribute to this SDK.

## About LaunchDarkly

* LaunchDarkly is a continuous delivery platform that provides feature flags as a service and allows developers to iterate quickly and safely. We allow you to easily flag your features and manage them from the LaunchDarkly dashboard.  With LaunchDarkly, you can:
    * Roll out a new feature to a subset of your users (like a group of users who opt-in to a beta tester group), gathering feedback and bug reports from real-world use cases.
    * Gradually roll out a feature to an increasing percentage of users, and track the effect that the feature has on key metrics (for instance, how likely is a user to complete a purchase if they have feature A versus feature B?).
    * Turn off a feature that you realize is causing performance problems in production, without needing to re-deploy, or even restart the application with a changed configuration file.
    * Grant access to certain features based on user attributes, like payment plan (eg: users on the ‘gold’ plan get access to more features than users in the ‘silver’ plan). Disable parts of your application to facilitate maintenance, without taking everything offline.
* LaunchDarkly provides feature flag SDKs for a wide variety of languages and technologies. Read [our documentation](https://docs.launchdarkly.com/sdk) for a complete list.
* Explore LaunchDarkly
    * [launchdarkly.com](https://www.launchdarkly.com/ "LaunchDarkly Main Website") for more information
    * [docs.launchdarkly.com](https://docs.launchdarkly.com/  "LaunchDarkly Documentation") for our documentation and SDK reference guides
    * [apidocs.launchdarkly.com](https://apidocs.launchdarkly.com/  "LaunchDarkly API Documentation") for our API documentation
    * [blog.launchdarkly.com](https://blog.launchdarkly.com/  "LaunchDarkly Blog Documentation") for the latest product updates
