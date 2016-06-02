package com.launchdarkly.client;

import java.util.List;

class Target {
  private List<String> values;
  private int variation;

  List<String> getValues() {
    return values;
  }

  int getVariation() {
    return variation;
  }
}
