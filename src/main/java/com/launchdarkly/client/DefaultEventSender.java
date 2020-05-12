package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.EventSender;
import com.launchdarkly.client.interfaces.EventSenderFactory;
import com.launchdarkly.client.interfaces.HttpConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import static com.launchdarkly.client.Util.configureHttpClientBuilder;
import static com.launchdarkly.client.Util.getHeadersBuilderFor;
import static com.launchdarkly.client.Util.httpErrorMessage;
import static com.launchdarkly.client.Util.isHttpErrorRecoverable;
import static com.launchdarkly.client.Util.shutdownHttpClient;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class DefaultEventSender implements EventSender {
  private static final Logger logger = LoggerFactory.getLogger(DefaultEventProcessor.class);
  
  private static final String EVENT_SCHEMA_HEADER = "X-LaunchDarkly-Event-Schema";
  private static final String EVENT_SCHEMA_VERSION = "3";
  private static final String EVENT_PAYLOAD_ID_HEADER = "X-LaunchDarkly-Payload-ID";
  private static final SimpleDateFormat HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
  private static final Object HTTP_DATE_FORMAT_LOCK = new Object(); // synchronize on this because DateFormat isn't thread-safe
  static final int DEFAULT_RETRY_DELAY_MILLIS = 1000;
  
  private final OkHttpClient httpClient;
  private final Headers baseHeaders;
  private final int retryDelayMillis;

  DefaultEventSender(
      String sdkKey,
      HttpConfiguration httpConfiguration,
      int retryDelayMillis
      ) {
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(httpConfiguration, httpBuilder);
    this.httpClient = httpBuilder.build();

    this.baseHeaders = getHeadersBuilderFor(sdkKey, httpConfiguration)
        .add("Content-Type", "application/json")
        .build();
    
    this.retryDelayMillis = retryDelayMillis <= 0 ? DEFAULT_RETRY_DELAY_MILLIS : retryDelayMillis;
  }
  
  @Override
  public void close() throws IOException {
    shutdownHttpClient(httpClient);
  }

  @Override
  public Result sendEventData(EventDataKind kind, String data, int eventCount, URI eventsBaseUri) {
    Headers.Builder headersBuilder = baseHeaders.newBuilder();
    URI uri;
    String description;
    
    switch (kind) {
    case ANALYTICS:
      uri = eventsBaseUri.resolve("bulk");
      String eventPayloadId = UUID.randomUUID().toString();
      headersBuilder.add(EVENT_PAYLOAD_ID_HEADER, eventPayloadId);
      headersBuilder.add(EVENT_SCHEMA_HEADER, EVENT_SCHEMA_VERSION);
      description = String.format("%d event(s)", eventCount);
      break;
    case DIAGNOSTICS:
      uri = eventsBaseUri.resolve("diagnostic");
      description = "diagnostic event";
      break;
    default:
      throw new IllegalArgumentException("kind");
    }
    
    Headers headers = headersBuilder.build();
    RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), data);
    boolean mustShutDown = false;
    
    logger.debug("Posting {} to {} with payload: {}", description, uri, data);

    for (int attempt = 0; attempt < 2; attempt++) {
      if (attempt > 0) {
        logger.warn("Will retry posting {} after {} milliseconds", description, retryDelayMillis);
        try {
          Thread.sleep(retryDelayMillis);
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
      
      try (Response response = httpClient.newCall(request).execute()) {
        long endTime = System.currentTimeMillis();
        logger.debug("{} delivery took {} ms, response status {}", description, endTime - startTime, response.code());
        
        if (response.isSuccessful()) {
          return new Result(true, false, parseResponseDate(response));
        }
        
        String logMessage = httpErrorMessage(response.code(), "posting " + description, nextActionMessage);
        if (isHttpErrorRecoverable(response.code())) {
          logger.warn(logMessage);
        } else {
          logger.error(logMessage);
          mustShutDown = true;
          break;
        }
      } catch (IOException e) {
        String message = "Unhandled exception when posting events - " + nextActionMessage + " (" + e.toString() + ")";
        logger.warn(message);
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
      return new DefaultEventSender(sdkKey, httpConfiguration, DefaultEventSender.DEFAULT_RETRY_DELAY_MILLIS);
    }
  }
}
