package com.launchdarkly.sdk.server;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class FlagChangeEventPublisher implements Closeable {
  private final List<FlagChangeListener> listeners = new ArrayList<>();
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private volatile ExecutorService executor = null;
  
  public void register(FlagChangeListener listener) {
    lock.writeLock().lock();
    try {
      listeners.add(listener);
      if (executor == null) {
        executor = createExecutorService();
      }
    } finally {
      lock.writeLock().unlock();
    }
  }
  
  public void unregister(FlagChangeListener listener) {
    lock.writeLock().lock();
    try {
      listeners.remove(listener);
    } finally {
      lock.writeLock().unlock();
    }    
  }
  
  public boolean hasListeners() {
    lock.readLock().lock();
    try {
      return !listeners.isEmpty();
    } finally {
      lock.readLock().unlock();
    }
  }
  
  public void publishEvent(FlagChangeEvent event) {
    FlagChangeListener[] ll;
    lock.readLock().lock();
    try {
      ll = listeners.toArray(new FlagChangeListener[listeners.size()]);
    } finally {
      lock.readLock().unlock();
    }
    for (FlagChangeListener l: ll) {
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
