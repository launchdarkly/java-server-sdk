package com.launchdarkly.client.flag;

import java.util.List;

public class Target {
  private List<String> values;
  private int variation;

  public List<String> getValues() {
    return values;
  }

  public int getVariation() {
    return variation;
  }
}
