package com.launchdarkly.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PollingProcessor implements UpdateProcessor {
  private static final Logger logger = LoggerFactory.getLogger(PollingProcessor.class);

  private final FeatureRequestor requestor;
  private final LDConfig config;
  private final FeatureStore store;
  private volatile boolean initialized = false;
  private ScheduledExecutorService scheduler = null;

  PollingProcessor(LDConfig config, FeatureRequestor requestor) {
    this.requestor = requestor;
    this.config = config;
    this.store = config.featureStore;
  }

  @Override
  public boolean initialized() {
    return initialized && config.featureStore.initialized();
  }

  @Override
  public void close() throws IOException {
    scheduler.shutdown();
  }

  @Override
  public Future<Void> start() {
    logger.info("Starting LaunchDarkly polling client with interval: "
        + config.pollingIntervalMillis + " milliseconds");
    final VeryBasicFuture initFuture = new VeryBasicFuture();
    scheduler = Executors.newScheduledThreadPool(1);

    scheduler.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        try {
          store.init(requestor.makeAllRequest(true));
          if (!initialized) {
            logger.info("Initialized LaunchDarkly client.");
            initialized = true;
            initFuture.completed(null);
          }
        } catch (IOException e) {
          logger.error("Encountered exception in LaunchDarkly client when retrieving update", e);
        }
      }
    }, 0L, config.pollingIntervalMillis, TimeUnit.MILLISECONDS);

    return initFuture;
  }
}
