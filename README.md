LaunchDarkly SDK for Java
=========================

![Circle CI](https://circleci.com/gh/launchdarkly/java-client.png)

Quick setup
-----------

0. Add the Java SDK to your project

        <dependency>
          <groupId>com.launchdarkly</groupId>
          <artifactId>launchdarkly-client</artifactId>
          <version>1.0.1</version>
        </dependency>

1. Import the LaunchDarkly package:

        import com.launchdarkly.client.*;

2. Create a new LDClient with your API key:

        LDClient ldClient = new LDClient("YOUR_API_KEY");

Your first feature flag
-----------------------

1. Create a new feature flag on your [dashboard](https://app.launchdarkly.com)
2. In your application code, use the feature's key to check wthether the flag is on for each user:

        LDUser user = new LDUser(username);
        boolean showFeature = ldClient.toggle("your.feature.key", user, false);
        if (showFeature) {
          // application code to show the feature 
        }
        else {
          // the code to run if the feature is off
        }

Troubleshooting
---------------
1. Json parsing exception: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was....ExceptionType: com.google.gson.JsonSyntaxException
   Make sure your build tool is bringing in the proper version of Gson. The LaunchDarkly SDK may have problems with versions earlier than 2.2.4. To enforce the proper version add this dependency:
   ```
   <dependency>
   	<groupId>com.google.code.gson</groupId>
   	<artifactId>gson</artifactId>
   	<version>2.2.4</version>
   </dependency>
   ```

1. SSL exceptions when calling toggle():
   Make sure your build tool is bringing in the proper version of the Apache http client. The LaunchDarkly SDK may have problems with versions earlier than 3.3.6. To enforce the proper version add this dependency:
   ```
   <dependency>
     <groupId>org.apache.httpcomponents</groupId>
     <artifactId>httpclient</artifactId>
     <version>4.3.6</version>
   </dependency>
   ```

Learn more
----------

Check out our [documentation](http://docs.launchdarkly.com) for in-depth instructions on configuring and using LaunchDarkly. You can also head straight to the [complete reference guide for this SDK](http://docs.launchdarkly.com/v1.0/docs/java-sdk-reference) or our [Javadocs](http://launchdarkly.github.io/java-client/).

Contributing
------------

We encourage pull-requests and other contributions from the community. We've also published an [SDK contributor's guide](http://docs.launchdarkly.com/v1.0/docs/sdk-contributors-guide) that provides a detailed explanation of how our SDKs work.

About LaunchDarkly
-----------

* LaunchDarkly is a continuous delivery platform that provides feature flags as a service and allows developers to iterate quickly and safely. We allow you to easily flag your features and manage them from the LaunchDarkly dashboard.  With LaunchDarkly, you can:
    * Roll out a new feature to a subset of your users (like a group of users who opt-in to a beta tester group), gathering feedback and bug reports from real-world use cases.
    * Gradually roll out a feature to an increasing percentage of users, and track the effect that the feature has on key metrics (for instance, how likely is a user to complete a purchase if they have feature A versus feature B?).
    * Turn off a feature that you realize is causing performance problems in production, without needing to re-deploy, or even restart the application with a changed configuration file.
    * Grant access to certain features based on user attributes, like payment plan (eg: users on the ‘gold’ plan get access to more features than users in the ‘silver’ plan). Disable parts of your application to facilitate maintenance, without taking everything offline.
* LaunchDarkly provides feature flag SDKs for
    * [Java](http://docs.launchdarkly.com/docs/java-sdk-reference "Java SDK")
    * [JavaScript] (http://docs.launchdarkly.com/docs/js-sdk-reference "LaunchDarkly JavaScript SDK")
    * [PHP] (http://docs.launchdarkly.com/docs/php-sdk-reference "LaunchDarkly PHP SDK")
    * [Python] (http://docs.launchdarkly.com/docs/python-sdk-reference "LaunchDarkly Python SDK")
    * [Go] (http://docs.launchdarkly.com/docs/go-sdk-reference "LaunchDarkly Go SDK")
    * [Node.JS] (http://docs.launchdarkly.com/docs/node-sdk-reference "LaunchDarkly Node SDK")
    * [.NET] (http://docs.launchdarkly.com/docs/dotnet-sdk-reference "LaunchDarkly .Net SDK")
    * [Ruby] (http://docs.launchdarkly.com/docs/ruby-sdk-reference "LaunchDarkly Ruby SDK")
    * [Python Twisted] (http://docs.launchdarkly.com/docs/python-twisted "LaunchDarkly Python Twisted SDK")
* Explore LaunchDarkly
    * [www.launchdarkly.com] (http://www.launchdarkly.com/ "LaunchDarkly Main Website") for more information
    * [docs.launchdarkly.com] (http://docs.launchdarkly.com/  "LaunchDarkly Documentation") for our documentation and SDKs
    * [apidocs.launchdarkly.com] (http://apidocs.launchdarkly.com/  "LaunchDarkly API Documentation") for our API documentation
    * [blog.launchdarkly.com] (http://blog.launchdarkly.com/  "LaunchDarkly Blog Documentation") for the latest product updates

