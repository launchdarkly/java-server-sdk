package com.launchdarkly.client;

import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PollingProcessor implements UpdateProcessor {
  private static final Logger logger = LoggerFactory.getLogger(PollingProcessor.class);

  private final FeatureRequestor requestor;
  private final LDConfig config;
  private final FeatureStore store;
  private AtomicBoolean initialized = new AtomicBoolean(false);
  private ScheduledExecutorService scheduler = null;

  PollingProcessor(LDConfig config, FeatureRequestor requestor) {
    this.requestor = requestor;
    this.config = config;
    this.store = config.featureStore;
  }

  @Override
  public boolean initialized() {
    return initialized.get();
  }

  @Override
  public void close() throws IOException {
    logger.info("Closing LaunchDarkly PollingProcessor");
    scheduler.shutdown();
  }

  @Override
  public Future<Void> start() {
    logger.info("Starting LaunchDarkly polling client with interval: "
        + config.pollingIntervalMillis + " milliseconds");
    final SettableFuture<Void> initFuture = SettableFuture.create();
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("LaunchDarkly-PollingProcessor-%d")
        .build();
    scheduler = Executors.newScheduledThreadPool(1, threadFactory);

    scheduler.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        try {
          store.init(requestor.getAllFlags());
          if (!initialized.getAndSet(true)) {
            logger.info("Initialized LaunchDarkly client.");
            initFuture.set(null);
          }
        } catch (IOException e) {
          logger.error("Encountered exception in LaunchDarkly client when retrieving update", e);
        }
      }
    }, 0L, config.pollingIntervalMillis, TimeUnit.MILLISECONDS);

    return initFuture;
  }
}
