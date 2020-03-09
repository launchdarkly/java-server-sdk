package com.launchdarkly.sdk.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataStoreUpdates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.launchdarkly.sdk.server.Util.httpErrorMessage;
import static com.launchdarkly.sdk.server.Util.isHttpErrorRecoverable;

final class PollingProcessor implements DataSource {
  private static final Logger logger = LoggerFactory.getLogger(PollingProcessor.class);

  @VisibleForTesting final FeatureRequestor requestor;
  private final DataStoreUpdates dataStoreUpdates;
  @VisibleForTesting final Duration pollInterval;
  private AtomicBoolean initialized = new AtomicBoolean(false);
  private ScheduledExecutorService scheduler = null;

  PollingProcessor(FeatureRequestor requestor, DataStoreUpdates dataStoreUpdates, Duration pollInterval) {
    this.requestor = requestor; // note that HTTP configuration is applied to the requestor when it is created
    this.dataStoreUpdates = dataStoreUpdates;
    this.pollInterval = pollInterval;
  }

  @Override
  public boolean isInitialized() {
    return initialized.get();
  }

  @Override
  public void close() throws IOException {
    logger.info("Closing LaunchDarkly PollingProcessor");
    if (scheduler != null) {
      scheduler.shutdown();
    }
    requestor.close();
  }

  @Override
  public Future<Void> start() {
    logger.info("Starting LaunchDarkly polling client with interval: "
        + pollInterval.toMillis() + " milliseconds");
    final SettableFuture<Void> initFuture = SettableFuture.create();
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("LaunchDarkly-PollingProcessor-%d")
        .build();
    scheduler = Executors.newScheduledThreadPool(1, threadFactory);

    scheduler.scheduleAtFixedRate(() -> {
      try {
        FeatureRequestor.AllData allData = requestor.getAllData();
        dataStoreUpdates.init(DefaultFeatureRequestor.toFullDataSet(allData));
        if (!initialized.getAndSet(true)) {
          logger.info("Initialized LaunchDarkly client.");
          initFuture.set(null);
        }
      } catch (HttpErrorException e) {
        logger.error(httpErrorMessage(e.getStatus(), "polling request", "will retry"));
        if (!isHttpErrorRecoverable(e.getStatus())) {
          scheduler.shutdown();
          initFuture.set(null); // if client is initializing, make it stop waiting; has no effect if already inited
        }
      } catch (IOException e) {
        logger.error("Encountered exception in LaunchDarkly client when retrieving update: {}", e.toString());
        logger.debug(e.toString(), e);
      }
    }, 0L, pollInterval.toMillis(), TimeUnit.MILLISECONDS);

    return initFuture;
  }
}