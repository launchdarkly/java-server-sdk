package com.launchdarkly.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.launchdarkly.client.Util.getRequestBuilder;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;

import okhttp3.Request;
import okhttp3.Response;

class FeatureRequestor {
  private static final Logger logger = LoggerFactory.getLogger(FeatureRequestor.class);
  private static final String GET_LATEST_FLAGS_PATH = "/sdk/latest-flags";
  private static final String GET_LATEST_SEGMENTS_PATH = "/sdk/latest-segments";
  private static final String GET_LATEST_ALL_PATH = "/sdk/latest-all";
  private final String sdkKey;
  private final LDConfig config;

  static class AllData {
    final Map<String, FeatureFlag> flags;
    final Map<String, Segment> segments;
    
    AllData(Map<String, FeatureFlag> flags, Map<String, Segment> segments) {
      this.flags = flags;
      this.segments = segments;
    }
  }
  
  FeatureRequestor(String sdkKey, LDConfig config) {
    this.sdkKey = sdkKey;
    this.config = config;
  }

  FeatureFlag getFlag(String featureKey) throws IOException, HttpErrorException {
    String body = get(GET_LATEST_FLAGS_PATH + "/" + featureKey);
    return config.gson.fromJson(body, FeatureFlag.class);
  }

  Segment getSegment(String segmentKey) throws IOException, HttpErrorException {
    String body = get(GET_LATEST_SEGMENTS_PATH + "/" + segmentKey);
    return config.gson.fromJson(body, Segment.class);
  }

  AllData getAllData() throws IOException, HttpErrorException {
    String body = get(GET_LATEST_ALL_PATH);
    return config.gson.fromJson(body, AllData.class);
  }
  
  static Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> toVersionedDataMap(AllData allData) {
    Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> ret = new HashMap<>();
    ret.put(FEATURES, allData.flags);
    ret.put(SEGMENTS, allData.segments);
    return ret;
  }
  
  private String get(String path) throws IOException, HttpErrorException {
    Request request = getRequestBuilder(sdkKey)
        .url(config.baseURI.resolve(path).toURL())
        .get()
        .build();

    logger.debug("Making request: " + request);

    try (Response response = config.httpClient.newCall(request).execute()) {
      String body = response.body().string();

      if (!response.isSuccessful()) {
        throw new HttpErrorException(response.code());
      }
      logger.debug("Get flag(s) response: " + response.toString() + " with body: " + body);
      logger.debug("Network response: " + response.networkResponse());
      if(!config.stream) {
        logger.debug("Cache hit count: " + config.httpClient.cache().hitCount() + " Cache network Count: " + config.httpClient.cache().networkCount());
        logger.debug("Cache response: " + response.cacheResponse());
      }

      return body;
    }
  }
}