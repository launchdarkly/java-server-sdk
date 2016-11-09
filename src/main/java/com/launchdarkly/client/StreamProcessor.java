package com.launchdarkly.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.ReadyState;
import okhttp3.Headers;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

class StreamProcessor implements UpdateProcessor {
  private static final String PUT = "put";
  private static final String PATCH = "patch";
  private static final String DELETE = "delete";
  private static final String INDIRECT_PUT = "indirect/put";
  private static final String INDIRECT_PATCH = "indirect/patch";
  private static final Logger logger = LoggerFactory.getLogger(StreamProcessor.class);
  private static final int DEAD_CONNECTION_INTERVAL_SECONDS = 300;

  private final FeatureStore store;
  private final LDConfig config;
  private final String sdkKey;
  private final FeatureRequestor requestor;
  private final ScheduledExecutorService heartbeatDetectorService;
  private volatile DateTime lastHeartbeat;
  private volatile EventSource es;
  private AtomicBoolean initialized = new AtomicBoolean(false);


  StreamProcessor(String sdkKey, LDConfig config, FeatureRequestor requestor) {
    this.store = config.featureStore;
    this.config = config;
    this.sdkKey = sdkKey;
    this.requestor = requestor;
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("LaunchDarkly-HeartbeatDetector-%d")
        .build();
    this.heartbeatDetectorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    heartbeatDetectorService.scheduleAtFixedRate(new HeartbeatDetector(), 1, 1, TimeUnit.MINUTES);
  }

  @Override
  public Future<Void> start() {
    final VeryBasicFuture initFuture = new VeryBasicFuture();

    Headers headers = new Headers.Builder()
        .add("Authorization", this.sdkKey)
        .add("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION)
        .add("Accept", "text/event-stream")
        .build();

    EventHandler handler = new EventHandler() {

      @Override
      public void onOpen() throws Exception {
      }

      @Override
      public void onMessage(String name, MessageEvent event) throws Exception {
        lastHeartbeat = DateTime.now();
        Gson gson = new Gson();
        switch (name) {
          case PUT:
            store.init(FeatureFlag.fromJsonMap(event.getData()));
            if (!initialized.getAndSet(true)) {
              initFuture.completed(null);
              logger.info("Initialized LaunchDarkly client.");
            }
            break;
          case PATCH: {
            FeaturePatchData data = gson.fromJson(event.getData(), FeaturePatchData.class);
            store.upsert(data.key(), data.feature());
            break;
          }
          case DELETE: {
            FeatureDeleteData data = gson.fromJson(event.getData(), FeatureDeleteData.class);
            store.delete(data.key(), data.version());
            break;
          }
          case INDIRECT_PUT:
            try {
              store.init(requestor.getAllFlags());
              if (!initialized.getAndSet(true)) {
                initFuture.completed(null);
                logger.info("Initialized LaunchDarkly client.");
              }
            } catch (IOException e) {
              logger.error("Encountered exception in LaunchDarkly client", e);
            }
            break;
          case INDIRECT_PATCH:
            String key = event.getData();
            try {
              FeatureFlag feature = requestor.getFlag(key);
              store.upsert(key, feature);
            } catch (IOException e) {
              logger.error("Encountered exception in LaunchDarkly client", e);
            }
            break;
          default:
            logger.warn("Unexpected event found in stream: " + event.getData());
            break;
        }
      }

      @Override
      public void onComment(String comment) {
        logger.debug("Received a heartbeat");
        lastHeartbeat = DateTime.now();
      }

      @Override
      public void onError(Throwable throwable) {
        logger.error("Encountered EventSource error: " + throwable.getMessage());
        logger.debug("", throwable);
      }
    };

    es = new EventSource.Builder(handler, URI.create(config.streamURI.toASCIIString() + "/flags"))
        .headers(headers)
        .reconnectTimeMs(config.reconnectTimeMs)
        .build();

    es.start();
    return initFuture;
  }

  @Override
  public void close() throws IOException {
    logger.info("Closing LaunchDarkly StreamProcessor");
    if (es != null) {
      es.close();
    }
    if (store != null) {
      store.close();
    }
    if (heartbeatDetectorService != null) {
      heartbeatDetectorService.shutdownNow();
      try {
        heartbeatDetectorService.awaitTermination(100, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        logger.error("Encountered an exception terminating heartbeat detector: " + e.getMessage());
      }
    }
  }

  @Override
  public boolean initialized() {
    return initialized.get();
  }

  FeatureFlag getFeature(String key) {
    return store.get(key);
  }

  private static final class FeaturePatchData {
    String path;
    FeatureFlag data;

    public FeaturePatchData() {

    }

    String key() {
      return path.substring(1);
    }

    FeatureFlag feature() {
      return data;
    }

  }

  private static final class FeatureDeleteData {
    String path;
    int version;

    public FeatureDeleteData() {

    }

    String key() {
      return path.substring(1);
    }

    int version() {
      return version;
    }

  }

  private final class HeartbeatDetector implements Runnable {

    @Override
    public void run() {
      DateTime reconnectThresholdTime = DateTime.now().minusSeconds(DEAD_CONNECTION_INTERVAL_SECONDS);
      // We only want to force the reconnect if the ES connection is open. If not, it's already trying to
      // connect anyway, or this processor was shut down
      if (lastHeartbeat.isBefore(reconnectThresholdTime) && es.getState() == ReadyState.OPEN) {
        try {
          logger.info("Stream stopped receiving heartbeats- reconnecting.");
          es.close();
        } catch (IOException e) {
          logger.error("Encountered exception closing stream connection: " + e.getMessage());
        } finally {
          if (es.getState() == ReadyState.SHUTDOWN) {
            start();
          } else {
            logger.error("Expected ES to be in state SHUTDOWN, but it's currently in state " + es.getState().toString());
          }
        }
      }
    }
  }
}
