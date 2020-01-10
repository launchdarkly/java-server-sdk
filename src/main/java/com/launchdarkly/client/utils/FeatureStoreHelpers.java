package com.launchdarkly.client.utils;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.launchdarkly.client.interfaces.FeatureStore;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;

/**
 * Helper methods that may be useful for implementing a {@link FeatureStore} or {@link FeatureStoreCore}.
 *
 * @since 4.6.0
 */
public abstract class FeatureStoreHelpers {
  private static final Gson gson = new Gson();
  
  /**
   * Unmarshals a feature store item from a JSON string. This is a very simple wrapper around a Gson
   * method, just to allow external feature store implementations to make use of the Gson instance
   * that's inside the SDK rather than having to import Gson themselves.
   * 
   * @param <T> class of the object that will be returned
   * @param kind specifies the type of item being decoded
   * @param data the JSON string
   * @return the unmarshaled item
   * @throws UnmarshalException if the JSON string was invalid
   */
  public static <T extends VersionedData> T unmarshalJson(VersionedDataKind<T> kind, String data) {
    try {
      return gson.fromJson(data, kind.getItemClass());
    } catch (JsonParseException e) {
      throw new UnmarshalException(e);
    }
  }
  
  /**
   * Marshals a feature store item into a JSON string. This is a very simple wrapper around a Gson
   * method, just to allow external feature store implementations to make use of the Gson instance
   * that's inside the SDK rather than having to import Gson themselves.
   * @param item the item to be marshaled
   * @return the JSON string
   */
  public static String marshalJson(VersionedData item) {
    return gson.toJson(item);
  }
  
  /**
   * Thrown by {@link FeatureStoreHelpers#unmarshalJson(VersionedDataKind, String)} for a deserialization error.
   */
  @SuppressWarnings("serial")
  public static class UnmarshalException extends RuntimeException {
    /**
     * Constructs an instance.
     * @param cause the underlying exception
     */
    public UnmarshalException(Throwable cause) {
      super(cause);
    }
  }
}
