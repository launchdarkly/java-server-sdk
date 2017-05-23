package com.launchdarkly.client;

import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

class FeatureRequestor {
  private static final Logger logger = LoggerFactory.getLogger(FeatureRequestor.class);
  private static final String GET_LATEST_FLAGS_PATH = "/sdk/latest-flags";
  private final String sdkKey;
  private final LDConfig config;

  FeatureRequestor(String sdkKey, LDConfig config) {
    this.sdkKey = sdkKey;
    this.config = config;
  }

  Map<String, FeatureFlag> getAllFlags() throws IOException {
    String body = get(GET_LATEST_FLAGS_PATH);
    return FeatureFlag.fromJsonMap(config, body);
  }

  FeatureFlag getFlag(String featureKey) throws IOException {
    String body = get(GET_LATEST_FLAGS_PATH + "/" + featureKey);
    return FeatureFlag.fromJson(config, body);
  }

  private String get(String path) throws IOException {
    Request request = config.getRequestBuilder(sdkKey)
        .url(config.baseURI.toString() + path)
        .get()
        .build();

    logger.debug("Making request: " + request);

    try (Response response = config.httpClient.newCall(request).execute()) {
      String body = response.body().string();

      if (!response.isSuccessful()) {
        if (response.code() == 401) {
          logger.error("[401] Invalid SDK key when accessing URI: " + request.url());
        }
        throw new IOException("Unexpected response when retrieving Feature Flag(s): " + response + " using url: "
            + request.url() + " with body: " + body);
      }
      logger.debug("Get flag(s) response: " + response.toString() + " with body: " + body);
      logger.debug("Cache hit count: " + config.httpClient.cache().hitCount() + " Cache network Count: " + config.httpClient.cache().networkCount());
      logger.debug("Cache response: " + response.cacheResponse());
      logger.debug("Network response: " + response.networkResponse());

      return body;
    }
  }
}
