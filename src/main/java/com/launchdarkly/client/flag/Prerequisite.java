package com.launchdarkly.client.flag;

public class Prerequisite {
  private String key;
  private int variation;

  public String getKey() {
    return key;
  }

  public int getVariation() {
    return variation;
  }
}
