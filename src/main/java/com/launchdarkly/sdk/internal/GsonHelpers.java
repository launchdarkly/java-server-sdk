package com.launchdarkly.sdk.internal;

import com.google.gson.Gson;

public abstract class GsonHelpers {
  private static final Gson GSON_INSTANCE = new Gson();
  
  public static Gson gsonInstance() {
    return GSON_INSTANCE;
  }
}
