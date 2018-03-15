package com.launchdarkly.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class EventProcessor implements Closeable {
  private static final Logger logger = LoggerFactory.getLogger(EventProcessor.class);
  private static final SimpleDateFormat HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
  private static final int CHANNEL_BLOCK_MILLIS = 1000;
  
  private final ScheduledExecutorService scheduler;
  private final Thread mainThread;
  private final BlockingQueue<EventProcessorMessage> inputChannel;
  private final ArrayList<Event> buffer;
  private final String sdkKey;
  private final LDConfig config;
  private final EventSummarizer summarizer;
  private final Random random = new Random();
  private final AtomicLong lastKnownPastTime = new AtomicLong(0);
  private final AtomicBoolean capacityExceeded = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  
  EventProcessor(String sdkKey, LDConfig config) {
    this.sdkKey = sdkKey;
    this.inputChannel = new ArrayBlockingQueue<>(config.capacity);
    this.buffer = new ArrayList<>(config.capacity);
    this.summarizer = new EventSummarizer(config);
    this.config = config;
    
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat("LaunchDarkly-EventProcessor-%d")
        .build();
    
    this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
    Runnable flusher = new Runnable() {
      public void run() {
        postMessageAsync(MessageType.FLUSH, null);
      }
    };
    this.scheduler.scheduleAtFixedRate(flusher, config.flushInterval, config.flushInterval, TimeUnit.SECONDS);
    Runnable userKeysFlusher = new Runnable() {
      public void run() {
        postMessageAsync(MessageType.FLUSH_USERS, null);
      }
    };
    this.scheduler.scheduleAtFixedRate(userKeysFlusher, config.userKeysFlushInterval, config.userKeysFlushInterval,
        TimeUnit.SECONDS);
    
    mainThread = threadFactory.newThread(new MainLoop());
    mainThread.start();
  }
  
  void sendEventAsync(Event e) {
    postMessageAsync(MessageType.EVENT, e);
  }
  
  boolean sendEvent(Event e) {
    return postMessageAndWait(MessageType.EVENT, e);
  }

  /**
   * This task drains the input queue as quickly as possible. Everything here is done on a single
   * thread so we don't have to synchronize on our internal structures; when it's time to flush,
   * dispatchFlush will fire off another task to do the part that takes longer.
   */
  private class MainLoop implements Runnable {
    public void run() {
      while (!stopped.get()) {
        try {
          EventProcessorMessage message = inputChannel.take();
          switch(message.type) {
          case EVENT:
            message.setResult(dispatchEvent(message.event));
            break;
          case FLUSH:
            dispatchFlush(message);
          case FLUSH_USERS:
            summarizer.resetUsers();
          }
        } catch (InterruptedException e) {
        } catch (Exception e) {
          logger.error("Unexpected error in event processor: " + e);
          logger.debug(e.getMessage(), e);
        }
      }
    }
  }
  
  private void postMessageAsync(MessageType type, Event event) {
    postToChannel(new EventProcessorMessage(type, event, false));
  }
  
  private boolean postMessageAndWait(MessageType type, Event event) {
    EventProcessorMessage message = new EventProcessorMessage(type, event, true);
    postToChannel(message);
    return message.waitForResult();
  }
  
  private void postToChannel(EventProcessorMessage message) {
    while (true) {
      try {
        if (inputChannel.offer(message, CHANNEL_BLOCK_MILLIS, TimeUnit.MILLISECONDS)) {
          break;
        } else {
          // This doesn't mean that the output event buffer is full, but rather that the main thread is
          // seriously backed up with not-yet-processed events. We shouldn't see this.
          logger.warn("Events are being produced faster than they can be processed");
        }
      } catch (InterruptedException ex) {
      }
    }
  }
  
  boolean dispatchEvent(Event e) {
    // For each user we haven't seen before, we add an index event - unless this is already
    // an identify event for that user.
    if (!config.inlineUsersInEvents && e.user != null && !summarizer.noticeUser(e.user)) {
      if (!(e instanceof IdentifyEvent)) {
        IndexEvent ie = new IndexEvent(e.creationDate, e.user);
        if (!queueEvent(ie)) {
          return false;
        }
      }
    }
    
    // Always record the event in the summarizer.
    summarizer.summarizeEvent(e);

    if (shouldTrackFullEvent(e)) {
      // Sampling interval applies only to fully-tracked events.
      if (config.samplingInterval > 0 && random.nextInt(config.samplingInterval) != 0) {
        return true;
      }
      // Queue the event as-is; we'll transform it into an output event when we're flushing
      // (to avoid doing that work on our main thread).
      return queueEvent(e);
    }
    return true;
  }
  
  private boolean queueEvent(Event e) {
    if (buffer.size() >= config.capacity) {
      if (capacityExceeded.compareAndSet(false, true)) {
        logger.warn("Exceeded event queue capacity. Increase capacity to avoid dropping events.");
      }
      return false;
    }
    capacityExceeded.set(false);
    buffer.add(e);
    return true;
  }
  
  private boolean shouldTrackFullEvent(Event e) {
    if (e instanceof FeatureRequestEvent) {
      FeatureRequestEvent fe = (FeatureRequestEvent)e;
      if (fe.trackEvents) {
        return true;
      }
      if (fe.debugEventsUntilDate != null) {
        // The "last known past time" comes from the last HTTP response we got from the server.
        // In case the client's time is set wrong, at least we know that any expiration date
        // earlier than that point is definitely in the past.
        long lastPast = lastKnownPastTime.get();
        if ((lastPast != 0 && fe.debugEventsUntilDate > lastPast) ||
            fe.debugEventsUntilDate > System.currentTimeMillis()) {
          return true;
        }
      }
      return false;
    } else {
      return true;
    }
  }
  
  private EventOutput createEventOutput(Event e) {
    String userKey = e.user == null ? null : e.user.getKeyAsString();
    if (e instanceof FeatureRequestEvent) {
      FeatureRequestEvent fe = (FeatureRequestEvent)e;
      boolean isDebug = (!fe.trackEvents && fe.debugEventsUntilDate != null);
      return new FeatureRequestEventOutput(fe.creationDate, fe.key,
          config.inlineUsersInEvents ? null : userKey,
          config.inlineUsersInEvents ? e.user : null,
          fe.version, fe.value, fe.defaultVal, fe.prereqOf, isDebug);
    } else if (e instanceof IdentifyEvent) {
      return new IdentifyEventOutput(e.creationDate, e.user);
    } else if (e instanceof CustomEvent) {
      CustomEvent ce = (CustomEvent)e;
      return new CustomEventOutput(ce.creationDate, ce.key,
          config.inlineUsersInEvents ? null : userKey,
          config.inlineUsersInEvents ? e.user : null,
          ce.data);
    } else if (e instanceof IndexEvent) {
      return (IndexEvent)e;
    } else {
      return null;
    }
  }

  @Override
  public void close() throws IOException {
    this.flush();
    scheduler.shutdown();
    stopped.set(true);
    mainThread.interrupt();
  }

  public void flush() {
    postMessageAndWait(MessageType.FLUSH, null);
  }

  private void dispatchFlush(EventProcessorMessage message) {
    Event[] events = buffer.toArray(new Event[buffer.size()]);
    buffer.clear();
    EventSummarizer.SummaryState snapshot = summarizer.snapshot();
    if (events.length > 0 || !snapshot.isEmpty()) {
      this.scheduler.schedule(new FlushTask(events, snapshot, message), 0, TimeUnit.SECONDS);      
    } else {
      message.setResult(true);
    }
  }
  
  class FlushTask implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(FlushTask.class);
    private final Event[] events;
    private final EventSummarizer.SummaryState snapshot;
    private final EventProcessorMessage message;
    
    FlushTask(Event[] events, EventSummarizer.SummaryState snapshot, EventProcessorMessage message) {
      this.events = events;
      this.snapshot = snapshot;
      this.message = message;
    }
    
    public void run() {
      try {
        List<EventOutput> eventsOut = new ArrayList<>(events.length + 1);
        for (Event event: events) {
          eventsOut.add(createEventOutput(event));
        }
        if (!snapshot.isEmpty()) {
          EventSummarizer.SummaryOutput summary = summarizer.output(snapshot);
          SummaryEventOutput seo = new SummaryEventOutput(summary.startDate, summary.endDate, summary.features);
          eventsOut.add(seo);
        }
        if (!eventsOut.isEmpty()) {
          postEvents(eventsOut);
        }
      } catch (Exception e) {
        logger.error("Unexpected error in event processor: " + e);
        logger.debug(e.getMessage(), e);
      }
      message.setResult(true);
    }
    
    private void postEvents(List<EventOutput> eventsOut) {
      String json = config.gson.toJson(eventsOut);
      logger.debug("Posting {} event(s) to {} with payload: {}",
          eventsOut.size(), config.eventsURI, json);

      Request request = config.getRequestBuilder(sdkKey)
          .url(config.eventsURI.toString() + "/bulk")
          .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json))
          .addHeader("Content-Type", "application/json")
          .build();

      try (Response response = config.httpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          logger.info("Got unexpected response when posting events: " + response);
          if (response.code() == 401) {
            stopped.set(true);
            logger.error("Received 401 error, no further events will be posted since SDK key is invalid");
            close();
          }
        } else {
          logger.debug("Events Response: " + response.code());
          try {
            String dateStr = response.header("Date");
            if (dateStr != null) {
              lastKnownPastTime.set(HTTP_DATE_FORMAT.parse(dateStr).getTime());
            }
          } catch (Exception e) {
          }
        }
      } catch (IOException e) {
        logger.info("Unhandled exception in LaunchDarkly client when posting events to URL: " + request.url(), e);
      }
    }
  }
  
  private static enum MessageType {
    EVENT,
    FLUSH,
    FLUSH_USERS
  }
  
  private static class EventProcessorMessage {
    private final MessageType type;
    private final Event event;
    private final AtomicBoolean result = new AtomicBoolean(false);
    private final Semaphore reply;
    
    private EventProcessorMessage(MessageType type, Event event, boolean sync) {
      this.type = type;
      this.event = event;
      reply = sync ? new Semaphore(0) : null;
    }
    
    void setResult(boolean value) {
      result.set(value);
      if (reply != null) {
        reply.release();
      }
    }
    
    boolean waitForResult() {
      if (reply == null) {
        return false;
      }
      while (true) {
        try {
          reply.acquire();
          return result.get();
        }
        catch (InterruptedException ex) {
        }
      }
    }
    
    @Override
    public String toString() {
      return ((event == null) ? type.toString() : (type + ": " + event.getClass().getSimpleName())) +
          (reply == null ? "" : " (sync)");
    }
  }
  
  private static interface EventOutput { }
  
  @SuppressWarnings("unused")
  private static class FeatureRequestEventOutput implements EventOutput {
    private final String kind;
    private final long creationDate;
    private final String key;
    private final String userKey;
    private final LDUser user;
    private final Integer version;
    private final JsonElement value;
    @SerializedName("default") private final JsonElement defaultVal;
    private final String prereqOf;
    
    FeatureRequestEventOutput(long creationDate, String key, String userKey, LDUser user,
        Integer version, JsonElement value, JsonElement defaultVal, String prereqOf, boolean debug) {
      this.kind = debug ? "debug" : "feature";
      this.creationDate = creationDate;
      this.key = key;
      this.userKey = userKey;
      this.user = user;
      this.version = version;
      this.value = value;
      this.defaultVal = defaultVal;
      this.prereqOf = prereqOf;
    }
  }

  @SuppressWarnings("unused")
  private static class IdentifyEventOutput extends Event implements EventOutput {
    private final String kind;
    private final String key;
    
    IdentifyEventOutput(long creationDate, LDUser user) {
      super(creationDate, user);
      this.kind = "identify";
      this.key = user.getKeyAsString();
    }
  }
  
  @SuppressWarnings("unused")
  private static class CustomEventOutput implements EventOutput {
    private final String kind;
    private final long creationDate;
    private final String key;
    private final String userKey;
    private final LDUser user;
    private final JsonElement data;
    
    CustomEventOutput(long creationDate, String key, String userKey, LDUser user, JsonElement data) {
      this.kind = "custom";
      this.creationDate = creationDate;
      this.key = key;
      this.userKey = userKey;
      this.user = user;
      this.data = data;
    }
  }
  
  @SuppressWarnings("unused")
  private static class IndexEvent extends Event implements EventOutput {
    private final String kind;
    
    IndexEvent(long creationDate, LDUser user) {
      super(creationDate, user);
      this.kind = "index";
    }
  }
  
  @SuppressWarnings("unused")
  private static class SummaryEventOutput implements EventOutput {
    private final String kind;
    private final long startDate;
    private final long endDate;
    private final Map<String, EventSummarizer.FlagSummaryData> features;
    
    SummaryEventOutput(long startDate, long endDate, Map<String, EventSummarizer.FlagSummaryData> features) {
      this.kind = "summary";
      this.startDate = startDate;
      this.endDate = endDate;
      this.features = features;
    }
  }
}
