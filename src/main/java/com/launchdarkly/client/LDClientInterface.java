package com.launchdarkly.client;

import com.google.gson.JsonElement;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

public interface LDClientInterface extends Closeable {
  boolean initialized();

  void track(String eventName, LDUser user, JsonElement data);

  void track(String eventName, LDUser user);

  void identify(LDUser user);

  Map<String, JsonElement> allFlags(LDUser user);

  boolean boolVariation(String featureKey, LDUser user, boolean defaultValue);

  @Deprecated
  boolean toggle(String featureKey, LDUser user, boolean defaultValue);

  Integer intVariation(String featureKey, LDUser user, int defaultValue);

  Double doubleVariation(String featureKey, LDUser user, Double defaultValue);

  String stringVariation(String featureKey, LDUser user, String defaultValue);

  JsonElement jsonVariation(String featureKey, LDUser user, JsonElement defaultValue);

  boolean isFlagKnown(String featureKey);

  @Override
  void close() throws IOException;

  void flush();

  boolean isOffline();

  String secureModeHash(LDUser user);
  
  String version();
}
