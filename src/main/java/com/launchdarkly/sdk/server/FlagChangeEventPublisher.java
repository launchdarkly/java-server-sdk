package com.launchdarkly.sdk.server;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

final class FlagChangeEventPublisher implements Closeable {
  private final CopyOnWriteArrayList<FlagChangeListener> listeners = new CopyOnWriteArrayList<>();
  private volatile ExecutorService executor = null;
  
  public void register(FlagChangeListener listener) {
    listeners.add(listener);
    synchronized (this) {
      if (executor == null) {
        executor = createExecutorService();
      }
    }
  }
  
  public void unregister(FlagChangeListener listener) {
    listeners.remove(listener);
  }
  
  public boolean hasListeners() {
    return !listeners.isEmpty();
  }
  
  public void publishEvent(FlagChangeEvent event) {
    for (FlagChangeListener l: listeners) {
      executor.execute(() -> {
        l.onFlagChange(event);
      });
    }
  }

  @Override
  public void close() throws IOException {
    if (executor != null) {
      executor.shutdown();
    }
  }
  
  private ExecutorService createExecutorService() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat("LaunchDarkly-FlagChangeEventPublisher-%d")
        .setPriority(Thread.MIN_PRIORITY)
        .build();
    return Executors.newCachedThreadPool(threadFactory);
  }
}
