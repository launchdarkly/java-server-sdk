package com.launchdarkly.client;

class Prerequisite {
  private final String key;
  private final int variation;

  Prerequisite(String key, int variation) {
    this.key = key;
    this.variation = variation;
  }

  String getKey() {
    return key;
  }

  int getVariation() {
    return variation;
  }
}
