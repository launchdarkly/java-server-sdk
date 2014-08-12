package com.launchdarkly.client;

class FeatureValue<T> {
  private T value;

  public FeatureValue() {

  }

  public T get() {
    return value;
  }
}
