package com.launchdarkly.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

class EventProcessor implements Closeable {
  private final ScheduledExecutorService scheduler;
  private final Random random = new Random();
  private final BlockingQueue<Event> queue;
  private final String sdkKey;
  private final LDConfig config;
  private final Consumer consumer;

  EventProcessor(String sdkKey, LDConfig config) {
    this.sdkKey = sdkKey;
    this.queue = new ArrayBlockingQueue<>(config.capacity);
    this.consumer = new Consumer(config);
    this.config = config;
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat("LaunchDarkly-EventProcessor-%d")
        .build();
    this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
    this.scheduler.scheduleAtFixedRate(consumer, 0, config.flushInterval, TimeUnit.SECONDS);
  }

  boolean sendEvent(Event e) {
    if (config.samplingInterval > 0 && random.nextInt(config.samplingInterval) != 0) {
      return true;
    }
    return queue.offer(e);
  }

  @Override
  public void close() throws IOException {
    scheduler.shutdown();
    this.flush();
  }

  public void flush() {
    this.consumer.flush();
  }

  class Consumer implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(Consumer.class);
    private final LDConfig config;
    private final AtomicBoolean shutdown;

    Consumer(LDConfig config) {
      this.config = config;
      this.shutdown = new AtomicBoolean(false);
    }

    @Override
    public void run() {
      flush();
    }

    public void flush() {
      List<Event> events = new ArrayList<>(queue.size());
      queue.drainTo(events);
      if (!events.isEmpty() && !shutdown.get()) {
        postEvents(events);
      }
    }

    private void postEvents(List<Event> events) {

      String json = LDConfig.gson.toJson(events);
      logger.debug("Posting " + events.size() + " event(s) to " + config.eventsURI + " with payload: " + json);

      String content = LDConfig.gson.toJson(events);

      Request request = config.getRequestBuilder(sdkKey)
          .url(config.eventsURI.toString() + "/bulk")
          .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), content))
          .addHeader("Content-Type", "application/json")
          .build();

      logger.debug("Posting " + events.size() + " event(s) using request: " + request);

      try (Response response = config.httpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          logger.info("Got unexpected response when posting events: " + response);
          if (response.code() == 401) {
            shutdown.set(true);
            logger.error("Received 401 error, no further events will be posted since SDK key is invalid");
            close();
          }
        } else {
          logger.debug("Events Response: " + response.code());
        }
      } catch (IOException e) {
        logger.info("Unhandled exception in LaunchDarkly client when posting events to URL: " + request.url(), e);
      }
    }
  }
}
