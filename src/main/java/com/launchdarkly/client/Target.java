package com.launchdarkly.client;

import java.util.List;

class Target {
  private final List<String> values;
  private final int variation;

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
