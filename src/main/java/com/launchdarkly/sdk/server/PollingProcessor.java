package com.launchdarkly.sdk.server;

import com.google.common.annotations.VisibleForTesting;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.SerializationException;

import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.launchdarkly.sdk.server.Util.checkIfErrorIsRecoverableAndLog;
import static com.launchdarkly.sdk.server.Util.httpErrorDescription;

final class PollingProcessor implements DataSource {
  private static final Logger logger = Loggers.DATA_SOURCE;
  private static final String ERROR_CONTEXT_MESSAGE = "on polling request";
  private static final String WILL_RETRY_MESSAGE = "will retry at next scheduled poll interval";

  @VisibleForTesting final FeatureRequestor requestor;
  private final DataSourceUpdates dataSourceUpdates;
  private final ScheduledExecutorService scheduler;
  @VisibleForTesting final Duration pollInterval;
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private volatile ScheduledFuture<?> task;
  private volatile CompletableFuture<Void> initFuture;

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
    
    // Even though the shared executor will be shut down when the LDClient is closed, it's still good
    // behavior to remove our polling task now - especially because we might be running in a test
    // environment where there isn't actually an LDClient.
    synchronized (this) {
      if (task != null) {
        task.cancel(false);
        task = null;
      }
    }
  }

  @Override
  public Future<Void> start() {
    logger.info("Starting LaunchDarkly polling client with interval: "
        + pollInterval.toMillis() + " milliseconds");
    
    synchronized (this) {
      if (initFuture != null) {
        return initFuture;
      }
      initFuture = new CompletableFuture<>();
      task = scheduler.scheduleAtFixedRate(this::poll, 0L, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }
    
    return initFuture;
  }
  
  private void poll() {
    FeatureRequestor.AllData allData = null;
    
    try {
      allData = requestor.getAllData();
    } catch (HttpErrorException e) {
      ErrorInfo errorInfo = ErrorInfo.fromHttpError(e.getStatus());
      boolean recoverable = checkIfErrorIsRecoverableAndLog(logger, httpErrorDescription(e.getStatus()),
          ERROR_CONTEXT_MESSAGE, e.getStatus(), WILL_RETRY_MESSAGE);
      if (recoverable) {
        dataSourceUpdates.updateStatus(State.INTERRUPTED, errorInfo);
      } else {
        dataSourceUpdates.updateStatus(State.OFF, errorInfo);
        initFuture.complete(null); // if client is initializing, make it stop waiting; has no effect if already inited
      }
    } catch (IOException e) {
      checkIfErrorIsRecoverableAndLog(logger, e.toString(), ERROR_CONTEXT_MESSAGE, 0, WILL_RETRY_MESSAGE);
      dataSourceUpdates.updateStatus(State.INTERRUPTED, ErrorInfo.fromException(ErrorKind.NETWORK_ERROR, e));
    } catch (SerializationException e) {
      logger.error("Polling request received malformed data: {}", e.toString());
      dataSourceUpdates.updateStatus(State.INTERRUPTED, ErrorInfo.fromException(ErrorKind.INVALID_DATA, e));
    } catch (Exception e) {
      logger.error("Unexpected error from polling processor: {}", e.toString());
      logger.debug(e.toString(), e);
      dataSourceUpdates.updateStatus(State.INTERRUPTED, ErrorInfo.fromException(ErrorKind.UNKNOWN, e));
    }
    
    if (allData != null && dataSourceUpdates.init(allData.toFullDataSet())) {
      if (!initialized.getAndSet(true)) {
        logger.info("Initialized LaunchDarkly client.");
        dataSourceUpdates.updateStatus(State.VALID, null);
        initFuture.complete(null);
      }
    }
  }
}
