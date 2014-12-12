package com.launchdarkly.client;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
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
import java.util.concurrent.*;

class EventProcessor implements Closeable {
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());
  private final BlockingQueue<Event> queue;
  private final String apiKey;

  EventProcessor(String apiKey, LDConfig config) {
    this.apiKey = apiKey;
    this.queue = new ArrayBlockingQueue<Event>(config.capacity);
    this.scheduler.scheduleAtFixedRate(new Consumer(config), 0, 10, TimeUnit.SECONDS);
  }

  boolean sendEvent(Event e) {
    return queue.offer(e);
  }

  @Override
  public void close() throws IOException {
    scheduler.shutdown();
  }

  static class DaemonThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable r) {
      Thread thread = new Thread(r);
      thread.setDaemon(true);
      return thread;
    }
  }

  class Consumer implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(Consumer.class);


    private final CloseableHttpClient client;
    private final LDConfig config;

    Consumer(LDConfig config) {
      this.config = config;
      client = HttpClients.createDefault();
    }

    @Override
    public void run() {
      List<Event> events = new ArrayList<Event>(queue.size());
      queue.drainTo(events);

      if (!events.isEmpty()) {
        postEvents(events);
      }
    }

    private void postEvents(List<Event> events) {
      CloseableHttpResponse response = null;
      Gson gson = new Gson();
      String json = gson.toJson(events);

      HttpPost request = config.postRequest(apiKey, "/api/events/bulk");
      StringEntity entity = new StringEntity(json, "UTF-8");
      entity.setContentType("application/json");
      request.setEntity(entity);

      try {
        response = client.execute(request);

        int status = response.getStatusLine().getStatusCode();

        if (status != HttpStatus.SC_OK) {
          if (status == HttpStatus.SC_UNAUTHORIZED) {
            logger.error("Invalid API key");
          }
          else {
            logger.error("Unexpected status code: " + status);
          }
        }
        else {
          logger.debug("Successfully processed events");
        }
      } catch (IOException e) {
        logger.error("Unhandled exception in LaunchDarkly client", e);
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
