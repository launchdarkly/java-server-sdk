package com.launchdarkly.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

class EventProcessor implements Closeable {
  private final ScheduledExecutorService scheduler;
  private final Random random = new Random();
  private final BlockingQueue<Event> queue;
  private final String apiKey;
  private final LDConfig config;
  private final Consumer consumer;

  EventProcessor(String apiKey, LDConfig config) {
    this.apiKey = apiKey;
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
    private final CloseableHttpClient client;
    private final LDConfig config;

    Consumer(LDConfig config) {
      this.config = config;
      client = HttpClients.custom().setProxy(config.proxyHost).build();
    }

    @Override
    public void run() {
      flush();
    }

    public void flush() {
      List<Event> events = new ArrayList<>(queue.size());
      queue.drainTo(events);

      if (!events.isEmpty()) {
        postEvents(events);
      }
    }

    private void postEvents(List<Event> events) {
      logger.debug("Posting " + events.size() + " event(s) to " + config.eventsURI);
      CloseableHttpResponse response = null;
      Gson gson = new Gson();
      String json = gson.toJson(events);

      HttpPost request = config.postEventsRequest(apiKey, "/bulk");
      StringEntity entity = new StringEntity(json, "UTF-8");
      entity.setContentType("application/json");
      request.setEntity(entity);

      try {
        response = client.execute(request);
        if (Util.handleResponse(logger, request, response)) {
          logger.debug("Successfully posted " + events.size() + " event(s).");
        }
      } catch (IOException e) {
        logger.error("Unhandled exception in LaunchDarkly client attempting to connect to URI: " + config.eventsURI, e);
      } finally {
        try {
          if (response != null) response.close();
        } catch (IOException e) {
          logger.error("Unhandled exception in LaunchDarkly client", e);
        }
      }
    }
  }
}
