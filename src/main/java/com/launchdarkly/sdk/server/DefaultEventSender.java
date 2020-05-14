package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.EventSender;
import com.launchdarkly.sdk.server.interfaces.EventSenderFactory;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import static com.launchdarkly.sdk.server.Util.checkIfErrorIsRecoverableAndLog;
import static com.launchdarkly.sdk.server.Util.configureHttpClientBuilder;
import static com.launchdarkly.sdk.server.Util.describeDuration;
import static com.launchdarkly.sdk.server.Util.getHeadersBuilderFor;
import static com.launchdarkly.sdk.server.Util.httpErrorDescription;
import static com.launchdarkly.sdk.server.Util.shutdownHttpClient;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class DefaultEventSender implements EventSender {
  private static final Logger logger = LoggerFactory.getLogger(DefaultEventProcessor.class);
  
  static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(1);
  private static final String EVENT_SCHEMA_HEADER = "X-LaunchDarkly-Event-Schema";
  private static final String EVENT_SCHEMA_VERSION = "3";
  private static final String EVENT_PAYLOAD_ID_HEADER = "X-LaunchDarkly-Payload-ID";
  private static final MediaType JSON_CONTENT_TYPE = MediaType.parse("application/json; charset=utf-8");
  private static final SimpleDateFormat HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
  private static final Object HTTP_DATE_FORMAT_LOCK = new Object(); // synchronize on this because DateFormat isn't thread-safe

  private final OkHttpClient httpClient;
  private final Headers baseHeaders;
  private final Duration retryDelay;

  DefaultEventSender(
      String sdkKey,
      HttpConfiguration httpConfiguration,
      Duration retryDelay
      ) {
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(httpConfiguration, httpBuilder);
    this.httpClient = httpBuilder.build();

    this.baseHeaders = getHeadersBuilderFor(sdkKey, httpConfiguration)
        .add("Content-Type", "application/json")
        .build();
    
    this.retryDelay = retryDelay == null ? DEFAULT_RETRY_DELAY : retryDelay;
  }
  
  @Override
  public void close() throws IOException {
    shutdownHttpClient(httpClient);
  }

  @Override
  public Result sendEventData(EventDataKind kind, String data, int eventCount, URI eventsBaseUri) {
    Headers.Builder headersBuilder = baseHeaders.newBuilder();
    String path;
    String description;
    
    switch (kind) {
    case ANALYTICS:
      path = "bulk";
      String eventPayloadId = UUID.randomUUID().toString();
      headersBuilder.add(EVENT_PAYLOAD_ID_HEADER, eventPayloadId);
      headersBuilder.add(EVENT_SCHEMA_HEADER, EVENT_SCHEMA_VERSION);
      description = String.format("%d event(s)", eventCount);
      break;
    case DIAGNOSTICS:
      path = "diagnostic";
      description = "diagnostic event";
      break;
    default:
      throw new IllegalArgumentException("kind");
    }
    
    URI uri = eventsBaseUri.resolve(eventsBaseUri.getPath().endsWith("/") ? path : ("/" + path));
    Headers headers = headersBuilder.build();
    RequestBody body = RequestBody.create(data, JSON_CONTENT_TYPE);
    boolean mustShutDown = false;
    
    logger.debug("Posting {} to {} with payload: {}", description, uri, data);

    for (int attempt = 0; attempt < 2; attempt++) {
      if (attempt > 0) {
        logger.warn("Will retry posting {} after {}", description, describeDuration(retryDelay));
        try {
          Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException e) {
        }
      }

      Request request = new Request.Builder()
          .url(uri.toASCIIString())
          .post(body)
          .headers(headers)
          .build();

      long startTime = System.currentTimeMillis();
      String nextActionMessage = attempt == 0 ? "will retry" : "some events were dropped";
      String errorContext = "posting " + description;
      
      try (Response response = httpClient.newCall(request).execute()) {
        long endTime = System.currentTimeMillis();
        logger.debug("{} delivery took {} ms, response status {}", description, endTime - startTime, response.code());
        
        if (response.isSuccessful()) {
          return new Result(true, false, parseResponseDate(response));
        }
        
        String errorDesc = httpErrorDescription(response.code());
        boolean recoverable = checkIfErrorIsRecoverableAndLog(
            logger,
            errorDesc,
            errorContext,
            response.code(),
            nextActionMessage
            );
        if (!recoverable) {
          mustShutDown = true;
          break;
        }
      } catch (IOException e) {
        checkIfErrorIsRecoverableAndLog(logger, e.toString(), errorContext, 0, nextActionMessage);
      }
    }
    
    return new Result(false, mustShutDown, null);
  }
  
  private static final Date parseResponseDate(Response response) {
    String dateStr = response.header("Date");
    if (dateStr != null) {
      try {
        // DateFormat is not thread-safe, so must synchronize
        synchronized (HTTP_DATE_FORMAT_LOCK) {
          return HTTP_DATE_FORMAT.parse(dateStr);
        }
      } catch (ParseException e) {
        logger.warn("Received invalid Date header from events service");
      }
    }
    return null;
  }
  
  static final class Factory implements EventSenderFactory {
    @Override
    public EventSender createEventSender(String sdkKey, HttpConfiguration httpConfiguration) {
      return new DefaultEventSender(sdkKey, httpConfiguration, DefaultEventSender.DEFAULT_RETRY_DELAY);
    }
  }
}
