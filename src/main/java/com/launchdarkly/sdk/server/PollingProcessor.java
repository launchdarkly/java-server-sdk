package com.launchdarkly.sdk.server;

import com.google.common.annotations.VisibleForTesting;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.SerializationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.launchdarkly.sdk.server.Util.httpErrorMessage;
import static com.launchdarkly.sdk.server.Util.isHttpErrorRecoverable;

final class PollingProcessor implements DataSource {
  private static final Logger logger = LoggerFactory.getLogger(PollingProcessor.class);

  @VisibleForTesting final FeatureRequestor requestor;
  private final DataSourceUpdates dataSourceUpdates;
  private final ScheduledExecutorService scheduler;
  @VisibleForTesting final Duration pollInterval;
  private AtomicBoolean initialized = new AtomicBoolean(false);

  PollingProcessor(
      FeatureRequestor requestor,
      DataSourceUpdates dataSourceUpdates,
      ScheduledExecutorService sharedExecutor,
      Duration pollInterval
      ) {
    this.requestor = requestor; // note that HTTP configuration is applied to the requestor when it is created
    this.dataSourceUpdates = dataSourceUpdates;
    this.scheduler = sharedExecutor;
    this.pollInterval = pollInterval;
  }

  @Override
  public boolean isInitialized() {
    return initialized.get();
  }

  @Override
  public void close() throws IOException {
    logger.info("Closing LaunchDarkly PollingProcessor");
    requestor.close();
  }

  @Override
  public Future<Void> start() {
    logger.info("Starting LaunchDarkly polling client with interval: "
        + pollInterval.toMillis() + " milliseconds");
    final CompletableFuture<Void> initFuture = new CompletableFuture<>();

    scheduler.scheduleAtFixedRate(() -> {
      try {
        FeatureRequestor.AllData allData = requestor.getAllData();
        dataSourceUpdates.init(allData.toFullDataSet());
        if (!initialized.getAndSet(true)) {
          logger.info("Initialized LaunchDarkly client.");
          dataSourceUpdates.updateStatus(DataSourceStatusProvider.State.VALID, null);
          initFuture.complete(null);
        }
      } catch (HttpErrorException e) {
        DataSourceStatusProvider.ErrorInfo errorInfo = new DataSourceStatusProvider.ErrorInfo(
            DataSourceStatusProvider.ErrorKind.ERROR_RESPONSE,
            e.getStatus(),
            null,
            Instant.now()
            );
        logger.error(httpErrorMessage(e.getStatus(), "polling request", "will retry"));
        if (isHttpErrorRecoverable(e.getStatus())) {
          dataSourceUpdates.updateStatus(DataSourceStatusProvider.State.INTERRUPTED, errorInfo);
        } else {
          dataSourceUpdates.updateStatus(DataSourceStatusProvider.State.OFF, errorInfo);
          initFuture.complete(null); // if client is initializing, make it stop waiting; has no effect if already inited
        }
      } catch (IOException e) {
        logger.error("Encountered exception in LaunchDarkly client when retrieving update: {}", e.toString());
        logger.debug(e.toString(), e);
        DataSourceStatusProvider.ErrorInfo errorInfo = new DataSourceStatusProvider.ErrorInfo(
            DataSourceStatusProvider.ErrorKind.NETWORK_ERROR,
            0,
            e.toString(),
            Instant.now()
            );
        dataSourceUpdates.updateStatus(DataSourceStatusProvider.State.INTERRUPTED, errorInfo);
      } catch (SerializationException e) {
        logger.error("Polling request received malformed data: {}", e.toString());
        DataSourceStatusProvider.ErrorInfo errorInfo = new DataSourceStatusProvider.ErrorInfo(
            DataSourceStatusProvider.ErrorKind.INVALID_DATA,
            0,
            e.toString(),
            Instant.now()
            );
        dataSourceUpdates.updateStatus(DataSourceStatusProvider.State.INTERRUPTED, errorInfo);
      } catch (Exception e) {
        logger.error("Unexpected error from polling processor: {}", e.toString());
        logger.debug(e.toString(), e);
        DataSourceStatusProvider.ErrorInfo errorInfo = new DataSourceStatusProvider.ErrorInfo(
            DataSourceStatusProvider.ErrorKind.UNKNOWN,
            0,
            e.toString(),
            Instant.now()
            ); 
        dataSourceUpdates.updateStatus(DataSourceStatusProvider.State.INTERRUPTED, errorInfo);
      }
    }, 0L, pollInterval.toMillis(), TimeUnit.MILLISECONDS);

    return initFuture;
  }
}