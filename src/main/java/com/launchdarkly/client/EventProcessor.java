package com.launchdarkly.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

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
import java.util.concurrent.locks.ReentrantLock;

class EventProcessor implements Closeable {
  private final ScheduledExecutorService scheduler;
  private final Random random = new Random();
  private final BlockingQueue<EventOutput> queue;
  private final String sdkKey;
  private final LDConfig config;
  private final Consumer consumer;
  private final EventSummarizer summarizer;
  private final ReentrantLock lock = new ReentrantLock();
  
  EventProcessor(String sdkKey, LDConfig config) {
    this.sdkKey = sdkKey;
    this.queue = new ArrayBlockingQueue<>(config.capacity);
    this.consumer = new Consumer(config);
    this.summarizer = new EventSummarizer(config);
    this.config = config;
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat("LaunchDarkly-EventProcessor-%d")
        .build();
    this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
    
    this.scheduler.scheduleAtFixedRate(consumer, 0, config.flushInterval, TimeUnit.SECONDS);
    Runnable userKeysFlusher = new Runnable() {
      public void run() {
        lock.lock();
        try {
          summarizer.resetUsers();
        } finally {
          lock.unlock();
        }
      }
    };
    this.scheduler.scheduleAtFixedRate(userKeysFlusher, 0, config.userKeysFlushInterval, TimeUnit.SECONDS);
  }

  boolean sendEvent(Event e) {
    lock.lock();
    try {
      if (!(e instanceof IdentifyEvent)) {
        // identify events don't get deduplicated and always include the full user data
        if (e.user != null && !summarizer.noticeUser(e.user)) {
          IndexEventOutput ie = new IndexEventOutput(e.creationDate, e.user);
          if (!queue.offer(ie)) {
            return false;
          }
        }
      }
      
      if (summarizer.summarizeEvent(e)) {
        return true;
      }
      
      if (config.samplingInterval > 0 && random.nextInt(config.samplingInterval) != 0) {
        return true;
      }
  
      EventOutput eventOutput = createEventOutput(e);
      if (eventOutput == null) {
        return false;
      } else {
        return queue.offer(eventOutput);
      }      
    } finally {
      lock.unlock();
    }
  }
  
  private EventOutput createEventOutput(Event e) {
    String userKey = e.user == null ? null : e.user.getKeyAsString();
    if (e instanceof FeatureRequestEvent) {
      FeatureRequestEvent fe = (FeatureRequestEvent)e;
      return new FeatureRequestEventOutput(fe.creationDate, fe.key, userKey,
          fe.variation, fe.version, fe.value, fe.defaultVal, fe.prereqOf,
          (!fe.trackEvents && fe.debugEventsUntilDate != null) ? Boolean.TRUE : null);
    } else if (e instanceof IdentifyEvent) {
      return new IdentifyEventOutput(e.creationDate, e.user);
    } else if (e instanceof CustomEvent) {
      CustomEvent ce = (CustomEvent)e;
      return new CustomEventOutput(e.creationDate, ce.key, userKey, ce.data);
    } else {
      return null;
    }
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
      List<EventOutput> events = new ArrayList<>(queue.size());
      EventSummarizer.EventsState snapshot;
      lock.lock();
      try {
        queue.drainTo(events);
        snapshot = summarizer.snapshot();
      } finally {
        lock.unlock();
      }
      if (!snapshot.counters.isEmpty()) {
        EventSummarizer.SummaryOutput summary = summarizer.output(snapshot);
        SummaryEventOutput seo = new SummaryEventOutput(summary.startDate, summary.endDate, summary.counters);
        events.add(seo);
      }
      if (!events.isEmpty() && !shutdown.get()) {
        postEvents(events);
      }
    }

    private void postEvents(List<EventOutput> events) {

      String json = config.gson.toJson(events);
      logger.debug("Posting " + events.size() + " event(s) to " + config.eventsURI + " with payload: " + json);

      String content = config.gson.toJson(events);

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
  
  private static interface EventOutput { }
  
  @SuppressWarnings("unused")
  private static class FeatureRequestEventOutput implements EventOutput {
    private final String kind;
    private final long creationDate;
    private final String key;
    private final String userKey;
    private final Integer variation;
    private final Integer version;
    private final JsonElement value;
    @SerializedName("default") private final JsonElement defaultVal;
    private final String prereqOf;
    private final Boolean debug;
    
    FeatureRequestEventOutput(long creationDate, String key, String userKey, Integer variation,
        Integer version, JsonElement value, JsonElement defaultVal, String prereqOf, Boolean debug) {
      this.kind = "feature";
      this.creationDate = creationDate;
      this.key = key;
      this.userKey = userKey;
      this.variation = variation;
      this.version = version;
      this.value = value;
      this.defaultVal = defaultVal;
      this.prereqOf = prereqOf;
      this.debug = debug;
    }
  }

  @SuppressWarnings("unused")
  private static class IdentifyEventOutput implements EventOutput {
    private final String kind;
    private final long creationDate;
    private final LDUser user;
    
    IdentifyEventOutput(long creationDate, LDUser user) {
      this.kind = "identify";
      this.creationDate = creationDate;
      this.user = user;
    }
  }
  
  @SuppressWarnings("unused")
  private static class CustomEventOutput implements EventOutput {
    private final String kind;
    private final long creationDate;
    private final String key;
    private final String userKey;
    private final JsonElement data;
    
    CustomEventOutput(long creationDate, String key, String userKey, JsonElement data) {
      this.kind = "custom";
      this.creationDate = creationDate;
      this.key = key;
      this.userKey = userKey;
      this.data = data;
    }
  }
  
  @SuppressWarnings("unused")
  private static class IndexEventOutput implements EventOutput {
    private final String kind;
    private final long creationDate;
    private final LDUser user;
    
    IndexEventOutput(long creationDate, LDUser user) {
      this.kind = "index";
      this.creationDate = creationDate;
      this.user = user;
    }
  }
  
  @SuppressWarnings("unused")
  private static class SummaryEventOutput implements EventOutput {
    private final String kind;
    private final long startDate;
    private final long endDate;
    private final List<EventSummarizer.CounterData> counters;
    
    SummaryEventOutput(long startDate, long endDate, List<EventSummarizer.CounterData> counters) {
      this.kind = "summary";
      this.startDate = startDate;
      this.endDate = endDate;
      this.counters = counters;
    }
  }
}
