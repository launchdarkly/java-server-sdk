package com.launchdarkly.client;

public class FeatureValue<T> {
  private T value;

  public FeatureValue() {

  }

  public T get() {
    return value;
  }
}
