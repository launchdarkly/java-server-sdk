package com.launchdarkly.client;

import java.util.List;

class Target {
  private List<String> values;
  private int variation;

  // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
  Target() {}

  Target(List<String> values, int variation) {
    this.values = values;
    this.variation = variation;
  }

  List<String> getValues() {
    return values;
  }

  int getVariation() {
    return variation;
  }
}
