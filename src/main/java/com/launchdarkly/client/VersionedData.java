package com.launchdarkly.client;

public interface VersionedData {
  String getKey();
  int getVersion();
  boolean isDeleted();
}
