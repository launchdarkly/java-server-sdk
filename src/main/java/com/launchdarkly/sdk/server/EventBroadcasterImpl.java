package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * A generic mechanism for registering event listeners and broadcasting events to them. The SDK maintains an
 * instance of this for each available type of listener (flag change, data store status, etc.). They are all
 * intended to share a single executor service; notifications are submitted individually to this service for
 * each listener. 
 * 
 * @param <ListenerT> the listener interface class
 * @param <EventT> the event class
 */
class EventBroadcasterImpl<ListenerT, EventT> {
  private final CopyOnWriteArrayList<ListenerT> listeners = new CopyOnWriteArrayList<>();
  private final BiConsumer<ListenerT, EventT> broadcastAction;
  private final ExecutorService executor;

  /**
   * Creates an instance.
   * 
   * @param broadcastAction a lambda that calls the appropriate listener method for an event
   * @param executor the executor to use for running notification tasks on a worker thread; if this
   *   is null (which should only be the case in test code) then broadcasting an event will be a no-op
   */
  EventBroadcasterImpl(BiConsumer<ListenerT, EventT> broadcastAction, ExecutorService executor) {
    this.broadcastAction = broadcastAction;
    this.executor = executor;
  }

  static EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> forFlagChangeEvents(ExecutorService executor) {
    return new EventBroadcasterImpl<>(FlagChangeListener::onFlagChange, executor);
  }
  
  static EventBroadcasterImpl<DataSourceStatusProvider.StatusListener, DataSourceStatusProvider.Status>
      forDataSourceStatus(ExecutorService executor) {
    return new EventBroadcasterImpl<>(DataSourceStatusProvider.StatusListener::dataSourceStatusChanged, executor);
  }

  static EventBroadcasterImpl<DataStoreStatusProvider.StatusListener, DataStoreStatusProvider.Status>
      forDataStoreStatus(ExecutorService executor) {
    return new EventBroadcasterImpl<>(DataStoreStatusProvider.StatusListener::dataStoreStatusChanged, executor);
  }

  static EventBroadcasterImpl<BigSegmentStoreStatusProvider.StatusListener, BigSegmentStoreStatusProvider.Status>
      forBigSegmentStoreStatus(ExecutorService executor) {
    return new EventBroadcasterImpl<>(BigSegmentStoreStatusProvider.StatusListener::bigSegmentStoreStatusChanged, executor);
  }

  /**
   * Registers a listener for this type of event. This method is thread-safe.
   * 
   * @param listener the listener to register
   */
  void register(ListenerT listener) {
    listeners.add(listener);
  }

  /**
   * Unregisters a listener. This method is thread-safe.
   * 
   * @param listener the listener to unregister
   */
  void unregister(ListenerT listener) {
    listeners.remove(listener);
  }
  
  /**
   * Returns true if any listeners are currently registered. This method is thread-safe.
   * 
   * @return true if there are listeners
   */
  boolean hasListeners() {
    return !listeners.isEmpty();
  }

  /**
   * Broadcasts an event to all available listeners.
   * 
   * @param event the event to broadcast
   */
  void broadcast(EventT event) {
    if (executor == null) {
      return;
    }
    for (ListenerT l: listeners) {
      executor.execute(() -> {
        try {
          broadcastAction.accept(l, event);
        } catch (Exception e) {
          Loggers.MAIN.warn("Unexpected error from listener ({}): {}", l.getClass(), e.toString());
          Loggers.MAIN.debug(e.toString(), e);
        }
      });
    }
  }
}
