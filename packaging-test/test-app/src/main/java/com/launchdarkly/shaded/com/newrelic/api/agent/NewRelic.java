package com.launchdarkly.shaded.com.newrelic.api.agent;

// Test to verify fix for https://github.com/launchdarkly/java-server-sdk/issues/171
public class NewRelic {
  public static void addCustomParameter(String name, String value) {
    System.out.println("NewRelic class reference was shaded! Test app loaded " + NewRelic.class.getName());
    System.exit(1); // forces test failure
  }	
}
