LaunchDarkly SDK for Java
=========================

Quick setup
-----------

0. Add the Java SDK to your project

        <dependency>
          <groupId>com.launchdarkly</groupId>
          <artifactId>launchdarkly-client</artifactId>
          <version>0.7.0</version>
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

Learn more
----------

Check out our [documentation](http://docs.launchdarkly.com) for in-depth instructions on configuring and using LaunchDarkly. You can also head straight to the [complete reference guide for your SDK](http://docs.launchdarkly.com/v1.0/docs/java-sdk-reference) or our [Javadocs](http://launchdarkly.github.io/java-client/).