package com.launchdarkly.client;

import java.util.Set;

class Target {
  private Set<String> values;
  private int variation;

  // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
  Target() {}

  Target(Set<String> values, int variation) {
    this.values = values;
    this.variation = variation;
  }

  Set<String> getValues() {
    return values;
  }

  int getVariation() {
    return variation;
  }
}
