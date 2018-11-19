package com.launchdarkly.client.utils;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.VersionedData;
import com.launchdarkly.client.VersionedDataKind;

/**
 * Helper methods that may be useful for implementing a {@link FeatureStore} or {@link FeatureStoreCore}.
 *
 * @since 4.6.0
 */
public abstract class FeatureStoreHelpers {
  private static final Gson gson = new Gson();
  
  public static <T extends VersionedData> T unmarshalJson(VersionedDataKind<T> kind, String data) {
    try {
      return gson.fromJson(data, kind.getItemClass());
    } catch (JsonParseException e) {
      throw new UnmarshalException(e);
    }
  }
  
  public static String marshalJson(VersionedData item) {
    return gson.toJson(item);
  }
  
  @SuppressWarnings("serial")
  public static class UnmarshalException extends RuntimeException {
    public UnmarshalException(Throwable cause) {
      super(cause);
    }
  }
}
