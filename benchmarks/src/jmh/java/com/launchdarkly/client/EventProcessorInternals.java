package com.launchdarkly.client;

import java.io.IOException;

// Placed here so we can access package-private SDK methods.
public class EventProcessorInternals {
  public static void waitUntilInactive(EventProcessor ep) {
    try {
      ((DefaultEventProcessor)ep).waitUntilInactive();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
