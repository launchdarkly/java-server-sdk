package com.launchdarkly.sdk.server;

import com.google.common.annotations.VisibleForTesting;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.internal.http.HttpErrors.HttpErrorException;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataSourceUpdateSink;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.SerializationException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.launchdarkly.sdk.internal.http.HttpErrors.checkIfErrorIsRecoverableAndLog;
import static com.launchdarkly.sdk.internal.http.HttpErrors.httpErrorDescription;

final class PollingProcessor implements DataSource {
  private static final String ERROR_CONTEXT_MESSAGE = "on polling request";
  private static final String WILL_RETRY_MESSAGE = "will retry at next scheduled poll interval";

  @VisibleForTesting final FeatureRequestor requestor;
  private final DataSourceUpdateSink dataSourceUpdates;
  private final ScheduledExecutorService scheduler;
  @VisibleForTesting final Duration pollInterval;
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final CompletableFuture<Void> initFuture;
  private volatile ScheduledFuture<?> task;
  private final LDLogger logger;

  PollingProcessor(
      FeatureRequestor requestor,
      DataSourceUpdateSink dataSourceUpdates,
      ScheduledExecutorService sharedExecutor,
      Duration pollInterval,
      LDLogger logger
      ) {
    this.requestor = requestor; // note that HTTP configuration is applied to the requestor when it is created
    this.dataSourceUpdates = dataSourceUpdates;
    this.scheduler = sharedExecutor;
    this.pollInterval = pollInterval;
    this.initFuture = new CompletableFuture<>();
    this.logger = logger;
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
        task.cancel(true);
        task = null;
      }
    }
  }

  @Override
  public Future<Void> start() {
    logger.info("Starting LaunchDarkly polling client with interval: {} milliseconds",
        pollInterval.toMillis());
    
    synchronized (this) {
      if (task == null) {
        task = scheduler.scheduleAtFixedRate(this::poll, 0L, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
      }
    }
    
    return initFuture;
  }
  
  private void poll() {
    try {
      // If we already obtained data earlier, and the poll request returns a cached response, then we don't
      // want to bother parsing the data or reinitializing the data store. But if we never succeeded in
      // storing any data, then we would still want to parse and try to store it even if it's cached.
      boolean alreadyInited = initialized.get();
      FullDataSet<ItemDescriptor> allData = requestor.getAllData(!alreadyInited);
      if (allData == null) {
        // This means it was cached, and alreadyInited was true
        dataSourceUpdates.updateStatus(State.VALID, null);
      } else {
        if (dataSourceUpdates.init(allData)) {
          dataSourceUpdates.updateStatus(State.VALID, null);
          if (!initialized.getAndSet(true)) {
            logger.info("Initialized LaunchDarkly client."); 
            initFuture.complete(null);
          }
        }
      }
    } catch (HttpErrorException e) {
      ErrorInfo errorInfo = ErrorInfo.fromHttpError(e.getStatus());
      boolean recoverable = checkIfErrorIsRecoverableAndLog(logger, httpErrorDescription(e.getStatus()),
          ERROR_CONTEXT_MESSAGE, e.getStatus(), WILL_RETRY_MESSAGE);
      if (recoverable) {
        dataSourceUpdates.updateStatus(State.INTERRUPTED, errorInfo);
      } else {
        dataSourceUpdates.updateStatus(State.OFF, errorInfo);
        initFuture.complete(null); // if client is initializing, make it stop waiting; has no effect if already inited
        if (task != null) {
          task.cancel(true);
          task = null;
        }
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
  }
}
