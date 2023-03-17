package com.launchdarkly.sdk.server;

import static com.launchdarkly.sdk.server.DataModelSerialization.parseFullDataSet;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.stream.JsonReader;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.internal.http.HttpConsts;
import com.launchdarkly.sdk.internal.http.HttpErrors.HttpErrorException;
import com.launchdarkly.sdk.internal.http.HttpHelpers;
import com.launchdarkly.sdk.internal.http.HttpProperties;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.SerializationException;

import okhttp3.Cache;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Implementation of getting flag data via a polling request.
 */
final class DefaultFeatureRequestor implements FeatureRequestor {
  private static final long MAX_HTTP_CACHE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

  private final OkHttpClient httpClient;
  @VisibleForTesting
  final URI pollingUri;
  private final Headers headers;
  private final Path cacheDir;
  private final LDLogger logger;

  /**
   * Creates a {@link DefaultFeatureRequestor}
   * 
   * @param httpProperties that will be used
   * @param baseUri        that will be used
   * @param payloadFilter  identifier that will be used to filter objects in the
   *                       payload, provide null for no filtering
   * @param logger         to log with
   */
  DefaultFeatureRequestor(HttpProperties httpProperties, URI baseUri, @Nullable String payloadFilter, LDLogger logger) {
    this.logger = logger;

    URI tempUri = HttpHelpers.concatenateUriPath(baseUri, StandardEndpoints.POLLING_REQUEST_PATH);
    if (payloadFilter != null) {
      if (!payloadFilter.isEmpty()) {
        tempUri = HttpHelpers.addQueryParam(tempUri, HttpConsts.QUERY_PARAM_FILTER, payloadFilter);
      } else {
        logger.info("Payload filter \"{}\" is not valid, not applying filter.", payloadFilter);
      }
    }
    this.pollingUri = tempUri;

    OkHttpClient.Builder httpBuilder = httpProperties.toHttpClientBuilder();
    this.headers = httpProperties.toHeadersBuilder().build();

    try {
      cacheDir = Files.createTempDirectory("LaunchDarklySDK");
    } catch (IOException e) {
      throw new RuntimeException("unable to create cache directory for polling", e);
    }
    Cache cache = new Cache(cacheDir.toFile(), MAX_HTTP_CACHE_SIZE_BYTES);
    httpBuilder.cache(cache);

    httpClient = httpBuilder.build();
  }

  public void close() {
    HttpProperties.shutdownHttpClient(httpClient);
    Util.deleteDirectory(cacheDir);
  }

  public FullDataSet<ItemDescriptor> getAllData(boolean returnDataEvenIfCached)
      throws IOException, HttpErrorException, SerializationException {
    Request request = new Request.Builder()
        .url(pollingUri.toURL())
        .headers(headers)
        .get()
        .build();

    logger.debug("Making request: " + request);

    try (Response response = httpClient.newCall(request).execute()) {
      boolean wasCached = response.networkResponse() == null || response.networkResponse().code() == 304;
      if (wasCached && !returnDataEvenIfCached) {
        logger.debug("Get flag(s) got cached response, will not parse");
        logger.debug("Cache hit count: {} Cache network count: {} ",
            httpClient.cache().hitCount(), httpClient.cache().networkCount());
        return null;
      }

      logger.debug("Get flag(s) response: {}", response);
      logger.debug("Network response: {}", response.networkResponse());
      logger.debug("Cache hit count: {} Cache network count: {}",
          httpClient.cache().hitCount(), httpClient.cache().networkCount());
      logger.debug("Cache response: {}", response.cacheResponse());

      if (!response.isSuccessful()) {
        throw new HttpErrorException(response.code());
      }

      JsonReader jr = new JsonReader(response.body().charStream());
      return parseFullDataSet(jr);
    }
  }
}
