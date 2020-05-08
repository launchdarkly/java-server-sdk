package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.Status;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.StatusListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Used internally to encapsulate the data store status broadcasting mechanism for PersistentDataStoreWrapper.
 * <p>
 * This is currently only used by PersistentDataStoreWrapper, but encapsulating it in its own class helps with
 * clarity and also lets us reuse this logic in tests.
 */
final class PersistentDataStoreStatusManager {
  private static final Logger logger = LoggerFactory.getLogger(PersistentDataStoreStatusManager.class);
  static final int POLL_INTERVAL_MS = 500; // visible for testing
  
  private final ScheduledExecutorService scheduler;
  private final EventBroadcasterImpl<DataStoreStatusProvider.StatusListener, DataStoreStatusProvider.Status> statusBroadcaster;
  private final Callable<Boolean> statusPollFn;
  private final boolean refreshOnRecovery;
  private volatile boolean lastAvailable;
  private volatile ScheduledFuture<?> pollerFuture;
  
  PersistentDataStoreStatusManager(
      boolean refreshOnRecovery,
      boolean availableNow,
      Callable<Boolean> statusPollFn,
      ScheduledExecutorService sharedExecutor
      ) {
    this.refreshOnRecovery = refreshOnRecovery;
    this.lastAvailable = availableNow;
    this.statusPollFn = statusPollFn;
    this.scheduler = sharedExecutor;
    this.statusBroadcaster = new EventBroadcasterImpl<>(
        DataStoreStatusProvider.StatusListener::dataStoreStatusChanged,
        sharedExecutor
        );
  }
  
  void addStatusListener(StatusListener listener) {
    statusBroadcaster.register(listener);
  }
  
  synchronized void removeStatusListener(StatusListener listener) {
    statusBroadcaster.unregister(listener);
  }
  
  void updateAvailability(boolean available) {
    synchronized (this) {
      if (lastAvailable == available) {
        return;
      }
      lastAvailable = available;
    }
    
    Status status = new Status(available, available && refreshOnRecovery);

    if (available) {
      logger.warn("Persistent store is available again");
    }

    statusBroadcaster.broadcast(status);
    
    // If the store has just become unavailable, start a poller to detect when it comes back. If it has
    // become available, stop any polling we are currently doing.
    if (available) {
      synchronized (this) {
        if (pollerFuture != null) {
          pollerFuture.cancel(false);
          pollerFuture = null;
        }
      }
    } else {
      logger.warn("Detected persistent store unavailability; updates will be cached until it recovers");
      
      // Start polling until the store starts working again
      Runnable pollerTask = new Runnable() {
        public void run() {
          try {
            if (statusPollFn.call()) {
              updateAvailability(true);
            }
          } catch (Exception e) {
            logger.error("Unexpected error from data store status function: {0}", e);
          }
        }
      };
      synchronized (this) {
        if (pollerFuture == null) {
          pollerFuture = scheduler.scheduleAtFixedRate(pollerTask, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);          
        }
      }
    }
  }
  
  synchronized boolean isAvailable() {
    return lastAvailable;
  }
}
