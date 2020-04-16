package com.launchdarkly.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;
import com.launchdarkly.client.interfaces.HttpConfiguration;
import com.launchdarkly.client.interfaces.SerializationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static com.launchdarkly.client.Util.configureHttpClientBuilder;
import static com.launchdarkly.client.Util.getHeadersBuilderFor;
import static com.launchdarkly.client.Util.shutdownHttpClient;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;

import okhttp3.Cache;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Implementation of getting flag data via a polling request. Used by both streaming and polling components.
 */
final class DefaultFeatureRequestor implements FeatureRequestor {
  private static final Logger logger = LoggerFactory.getLogger(DefaultFeatureRequestor.class);
  private static final String GET_LATEST_FLAGS_PATH = "/sdk/latest-flags";
  private static final String GET_LATEST_SEGMENTS_PATH = "/sdk/latest-segments";
  private static final String GET_LATEST_ALL_PATH = "/sdk/latest-all";
  private static final long MAX_HTTP_CACHE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
  
  @VisibleForTesting final URI baseUri;
  private final OkHttpClient httpClient;
  private final Headers headers;
  private final boolean useCache;

  DefaultFeatureRequestor(String sdkKey, HttpConfiguration httpConfig, URI baseUri, boolean useCache) {
    this.baseUri = baseUri;
    this.useCache = useCache;
    
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(httpConfig, httpBuilder);
    this.headers = getHeadersBuilderFor(sdkKey, httpConfig).build();

    // HTTP caching is used only for FeatureRequestor. However, when streaming is enabled, HTTP GETs
    // made by FeatureRequester will always guarantee a new flag state, so we disable the cache.
    if (useCache) {
      File cacheDir = Files.createTempDir();
      Cache cache = new Cache(cacheDir, MAX_HTTP_CACHE_SIZE_BYTES);
      httpBuilder.cache(cache);
    }

    httpClient = httpBuilder.build();
  }

  public void close() {
    shutdownHttpClient(httpClient);
  }
  
  public FeatureFlag getFlag(String featureKey) throws IOException, HttpErrorException, SerializationException {
    String body = get(GET_LATEST_FLAGS_PATH + "/" + featureKey);
    return JsonHelpers.deserialize(body, FeatureFlag.class);
  }

  public Segment getSegment(String segmentKey) throws IOException, HttpErrorException {
    String body = get(GET_LATEST_SEGMENTS_PATH + "/" + segmentKey);
    return JsonHelpers.deserialize(body, Segment.class);
  }

  public AllData getAllData() throws IOException, HttpErrorException {
    String body = get(GET_LATEST_ALL_PATH);
    return JsonHelpers.deserialize(body, AllData.class);
  }
  
  static Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> toVersionedDataMap(AllData allData) {
    Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> ret = new HashMap<>();
    ret.put(FEATURES, allData.flags);
    ret.put(SEGMENTS, allData.segments);
    return ret;
  }
  
  private String get(String path) throws IOException, HttpErrorException {
    Request request = new Request.Builder()
        .url(baseUri.resolve(path).toURL())
        .headers(headers)
        .get()
        .build();

    logger.debug("Making request: " + request);

    try (Response response = httpClient.newCall(request).execute()) {
      String body = response.body().string();

      if (!response.isSuccessful()) {
        throw new HttpErrorException(response.code());
      }
      logger.debug("Get flag(s) response: " + response.toString() + " with body: " + body);
      logger.debug("Network response: " + response.networkResponse());
      if (useCache) {
        logger.debug("Cache hit count: " + httpClient.cache().hitCount() + " Cache network Count: " + httpClient.cache().networkCount());
        logger.debug("Cache response: " + response.cacheResponse());
      }

      return body;
    }
  }
}