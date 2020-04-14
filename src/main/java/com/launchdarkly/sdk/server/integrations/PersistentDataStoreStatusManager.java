package com.launchdarkly.sdk.server.integrations;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.Status;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.StatusListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Used internally to encapsulate the data store status broadcasting mechanism for PersistentDataStoreWrapper.
 * <p>
 * This is currently only used by PersistentDataStoreWrapper, but encapsulating it in its own class helps with
 * clarity and also lets us reuse this logic in tests.
 */
final class PersistentDataStoreStatusManager {
  private static final Logger logger = LoggerFactory.getLogger(PersistentDataStoreStatusManager.class);
  private static final int POLL_INTERVAL_MS = 500;
  
  private final List<DataStoreStatusProvider.StatusListener> listeners = new ArrayList<>();
  private final ScheduledExecutorService scheduler;
  private final Callable<Boolean> statusPollFn;
  private final boolean refreshOnRecovery;
  private volatile boolean lastAvailable;
  private volatile ScheduledFuture<?> pollerFuture;
  
  PersistentDataStoreStatusManager(boolean refreshOnRecovery, boolean availableNow, Callable<Boolean> statusPollFn) {
    this.refreshOnRecovery = refreshOnRecovery;
    this.lastAvailable = availableNow;
    this.statusPollFn = statusPollFn;
    
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("LaunchDarkly-DataStoreStatusManager-%d")
        .build();
    scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
    // Using newSingleThreadScheduledExecutor avoids ambiguity about execution order if we might have
    // have a StatusNotificationTask happening soon after another one. 
  }
  
  synchronized void addStatusListener(StatusListener listener) {
    listeners.add(listener);
  }
  
  synchronized void removeStatusListener(StatusListener listener) {
    listeners.remove(listener);
  }
  
  void updateAvailability(boolean available) {
    StatusListener[] copyOfListeners = null;
    synchronized (this) {
      if (lastAvailable == available) {
        return;
      }
      lastAvailable = available;
      copyOfListeners = listeners.toArray(new StatusListener[listeners.size()]);
    }
    
    StatusImpl status = new StatusImpl(available, available && refreshOnRecovery);

    if (available) {
      logger.warn("Persistent store is available again");
    }

    // Notify all the subscribers (on a worker thread, so we can't be blocked by a slow listener).
    if (copyOfListeners.length > 0) {
      scheduler.schedule(new StatusNotificationTask(status, copyOfListeners), 0, TimeUnit.MILLISECONDS);
    }
    
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
  
  void close() {
    scheduler.shutdown();
  }
  
  static final class StatusImpl implements Status {
    private final boolean available;
    private final boolean needsRefresh;
    
    StatusImpl(boolean available, boolean needsRefresh) {
      this.available = available;
      this.needsRefresh = needsRefresh;
    }

    @Override
    public boolean isAvailable() {
      return available;
    }

    @Override
    public boolean isRefreshNeeded() {
      return needsRefresh;
    }
  }
  
  private static final class StatusNotificationTask implements Runnable {
    private final Status status;
    private final StatusListener[] listeners;
    
    StatusNotificationTask(Status status, StatusListener[] listeners) {
      this.status = status;
      this.listeners = listeners;
    }
    
    public void run() {
      for (StatusListener listener: listeners) {
        try {
          listener.dataStoreStatusChanged(status);
        } catch (Exception e) {
          logger.error("Unexpected error from StatusListener: {0}", e);
        }
      }
    }
  }
}
