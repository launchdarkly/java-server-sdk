package com.launchdarkly.client;

class FeatureRequestEvent<E> extends Event {
  E value;

  FeatureRequestEvent(String key, LDUser user, E value) {
    super("feature", key, user);
    this.value = value;
  }
}
