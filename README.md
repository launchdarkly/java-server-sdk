LaunchDarkly Server-side SDK for Java
=========================

[![Circle CI](https://circleci.com/gh/launchdarkly/java-server-sdk.svg?style=shield)](https://circleci.com/gh/launchdarkly/java-server-sdk)
[![Javadocs](http://javadoc.io/badge/com.launchdarkly/launchdarkly-java-server-sdk.svg)](http://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk)

LaunchDarkly overview
-------------------------
[LaunchDarkly](https://www.launchdarkly.com) is a feature management platform that serves over 100 billion feature flags daily to help teams build better software, faster. [Get started](https://docs.launchdarkly.com/docs/getting-started) using LaunchDarkly today!
 
[![Twitter Follow](https://img.shields.io/twitter/follow/launchdarkly.svg?style=social&label=Follow&maxAge=2592000)](https://twitter.com/intent/follow?screen_name=launchdarkly)

Supported Java versions
-----------------------

This version of the LaunchDarkly SDK works with Java 8 and above.

Distributions
-------------

Three variants of the SDK jar are published to Maven:

* The default uberjar - this is accessible as `com.launchdarkly:launchdarkly-java-server-sdk:jar` and is the dependency used in the "[Getting started](https://docs.launchdarkly.com/docs/java-sdk-reference#section-getting-started)" section of the SDK reference guide as well as in the [`hello-java`](https://github.com/launchdarkly/hello-java) sample app. This variant contains the SDK classes, and all of the SDK's dependencies except for SLF4J, which must be provided by the host application. The bundled dependencies have shaded package names (and are not exported in OSGi), so they will not interfere with any other versions of the same packages.
* The extended uberjar - add `<classifier>all</classifier>` in Maven, or `:all` in Gradle. This is the same as the default uberjar except that SLF4J is also bundled, without shading (and is exported in OSGi).
* The "thin" jar - add `<classifier>thin</classifier>` in Maven, or `:thin` in Gradle. This contains _only_ the SDK classes.

Getting started
-----------

Refer to the [SDK reference guide](https://docs.launchdarkly.com/docs/java-sdk-reference#section-getting-started) for instructions on getting started with using the SDK.

Logging
-------

The LaunchDarkly SDK uses [SLF4J](https://www.slf4j.org/). All loggers are namespaced under `com.launchdarkly`. For an example configuration check out the [hello-java](https://github.com/launchdarkly/hello-java) project.

Be aware of two considerations when enabling the DEBUG log level:
1. Debug-level logs can be very verbose. It is not recommended that you turn on debug logging in high-volume environments.
1. Potentially sensitive information is logged including LaunchDarkly users created by you in your usage of this SDK.

Using flag data from a file
---------------------------

For testing purposes, the SDK can be made to read feature flag state from a file or files instead of connecting to LaunchDarkly. See <a href="http://javadoc.io/page/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/client/files/FileComponents.html">FileComponents</a> for more details.

DNS caching issues
------------------

LaunchDarkly servers operate in a load-balancing framework which may cause their IP addresses to change. This could result in the SDK failing to connect to LaunchDarkly if an old IP address is still in your system's DNS cache.

Unlike some other languages, in Java the DNS caching behavior is controlled by the Java virtual machine rather than the operating system. The default behavior varies depending on whether there is a [security manager](https://docs.oracle.com/javase/tutorial/essential/environment/security.html): if there is, IP addresses will _never_ expire. In that case, we recommend that you set the security property `networkaddress.cache.ttl`, as described [here](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-jvm-ttl.html), to a number of seconds such as 30 or 60 (a lower value will reduce the chance of intermittent failures, but will slightly reduce networking performance).

Learn more
----------

Check out our [documentation](https://docs.launchdarkly.com) for in-depth instructions on configuring and using LaunchDarkly. You can also head straight to the [complete reference guide for this SDK](https://docs.launchdarkly.com/docs/java-sdk-reference) or our [code-generated API documentation](https://launchdarkly.github.io/java-server-sdk/).

Testing
-------

We run integration tests for all our SDKs using a centralized test harness. This approach gives us the ability to test for consistency across SDKs, as well as test networking behavior in a long-running application. These tests cover each method in the SDK, and verify that event sending, flag evaluation, stream reconnection, and other aspects of the SDK all behave correctly.

Contributing
------------

We encourage pull requests and other contributions from the community. Check out our [contributing guidelines](CONTRIBUTING.md) for instructions on how to contribute to this SDK.

About LaunchDarkly
-----------

* LaunchDarkly is a continuous delivery platform that provides feature flags as a service and allows developers to iterate quickly and safely. We allow you to easily flag your features and manage them from the LaunchDarkly dashboard.  With LaunchDarkly, you can:
    * Roll out a new feature to a subset of your users (like a group of users who opt-in to a beta tester group), gathering feedback and bug reports from real-world use cases.
    * Gradually roll out a feature to an increasing percentage of users, and track the effect that the feature has on key metrics (for instance, how likely is a user to complete a purchase if they have feature A versus feature B?).
    * Turn off a feature that you realize is causing performance problems in production, without needing to re-deploy, or even restart the application with a changed configuration file.
    * Grant access to certain features based on user attributes, like payment plan (eg: users on the ‘gold’ plan get access to more features than users in the ‘silver’ plan). Disable parts of your application to facilitate maintenance, without taking everything offline.
* LaunchDarkly provides feature flag SDKs for a wide variety of languages and technologies. Check out [our documentation](https://docs.launchdarkly.com/docs) for a complete list.
* Explore LaunchDarkly
    * [launchdarkly.com](https://www.launchdarkly.com/ "LaunchDarkly Main Website") for more information
    * [docs.launchdarkly.com](https://docs.launchdarkly.com/  "LaunchDarkly Documentation") for our documentation and SDK reference guides
    * [apidocs.launchdarkly.com](https://apidocs.launchdarkly.com/  "LaunchDarkly API Documentation") for our API documentation
    * [blog.launchdarkly.com](https://blog.launchdarkly.com/  "LaunchDarkly Blog Documentation") for the latest product updates
    * [Feature Flagging Guide](https://github.com/launchdarkly/featureflags/  "Feature Flagging Guide") for best practices and strategies
