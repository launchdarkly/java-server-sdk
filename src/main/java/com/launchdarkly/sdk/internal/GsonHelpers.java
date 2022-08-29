package com.launchdarkly.sdk.internal;

import com.google.gson.Gson;

/**
 * General-purpose Gson helpers.
 */
public abstract class GsonHelpers {
  private static final Gson GSON_INSTANCE = new Gson();
  
  /**
   * A singleton instance of Gson with the default configuration.
   * @return a Gson instance
   */
  public static Gson gsonInstance() {
    return GSON_INSTANCE;
  }
}
