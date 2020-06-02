package com.launchdarkly.sdk.json;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.sdk.server.FeatureFlagsState;

// See JsonSerialization.getDeserializableClasses in java-sdk-common.

abstract class SdkSerializationExtensions {
  private SdkSerializationExtensions() {}
  
  public static Iterable<Class<? extends JsonSerializable>> getDeserializableClasses() {
    return ImmutableList.<Class<? extends JsonSerializable>>of(
        FeatureFlagsState.class
    );
  }
}
