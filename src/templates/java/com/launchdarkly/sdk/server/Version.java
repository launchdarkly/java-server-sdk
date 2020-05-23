package com.launchdarkly.sdk.server;

abstract class Version {
  private Version() {}
  
  // This constant is updated automatically by our Gradle script during a release, if the project version has changed
  static final String SDK_VERSION = "@VERSION@";
}
