package com.newrelic.api.agent;

// Test to verify fix for https://github.com/launchdarkly/java-server-sdk/issues/171
public class NewRelic {
  public static void addCustomParameter(String name, String value) {
    System.out.println("NewRelic class reference was correctly resolved without shading");
  }	
}
