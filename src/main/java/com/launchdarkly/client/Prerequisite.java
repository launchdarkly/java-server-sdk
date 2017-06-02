package com.launchdarkly.client;

class Prerequisite {
  private String key;
  private int variation;

  // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
  Prerequisite() {}

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
