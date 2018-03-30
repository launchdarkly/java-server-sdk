package com.launchdarkly.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.launchdarkly.client.EventSummarizer.CounterKey;
import com.launchdarkly.client.EventSummarizer.CounterValue;
import com.launchdarkly.client.EventSummarizer.EventSummary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class DefaultEventProcessor implements EventProcessor {
  private static final Logger logger = LoggerFactory.getLogger(DefaultEventProcessor.class);
  static final SimpleDateFormat HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
  private static final int CHANNEL_BLOCK_MILLIS = 1000;
  
  private final BlockingQueue<EventProcessorMessage> inputChannel;
  private final ThreadFactory threadFactory;
  private final ScheduledExecutorService scheduler;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicBoolean inputCapacityExceeded = new AtomicBoolean(false);
  
  DefaultEventProcessor(String sdkKey, LDConfig config) {
    inputChannel = new ArrayBlockingQueue<>(config.capacity);

    threadFactory = new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat("LaunchDarkly-EventProcessor-%d")
        .build();
    scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);

    new EventConsumer(sdkKey, config, inputChannel, threadFactory);

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
  }
  
  @Override
  public void sendEvent(Event e) {
    if (!closed.get()) {
      postMessageAsync(MessageType.EVENT, e);
    }
  }
  
  @Override
  public void flush() {
    if (!closed.get()) {
      postMessageAsync(MessageType.FLUSH, null);
    }
  }

  @Override
  public void close() throws IOException {
    if (closed.compareAndSet(false, true)) {
      scheduler.shutdown();
      postMessageAsync(MessageType.FLUSH, null);
      postMessageAndWait(MessageType.SHUTDOWN, null);
    }
  }
  
  @VisibleForTesting
  void waitUntilInactive() throws IOException {
    // Waits until there are no pending events or flushes
    postMessageAndWait(MessageType.SYNC, null);
  }
  
  private void postMessageAsync(MessageType type, Event event) {
    postToChannel(new EventProcessorMessage(type, event, false));
  }
  
  private void postMessageAndWait(MessageType type, Event event) {
    EventProcessorMessage message = new EventProcessorMessage(type, event, true);
    postToChannel(message);
    message.waitForCompletion();
  }
  
  private void postToChannel(EventProcessorMessage message) {
    while (true) {
      try {
        if (inputChannel.offer(message, CHANNEL_BLOCK_MILLIS, TimeUnit.MILLISECONDS)) {
          inputCapacityExceeded.set(false);
          break;
        } else {
          // This doesn't mean that the output event buffer is full, but rather that the main thread is
          // seriously backed up with not-yet-processed events. We shouldn't see this.
          if (inputCapacityExceeded.compareAndSet(false, true)) {
            logger.warn("Events are being produced faster than they can be processed");
          }
        }
      } catch (InterruptedException ex) {
      }
    }
  }
  
  /**
   * Takes messages from the input queue, updating the event buffer and summary counters
   * on its own thread.
   */
  private static class EventConsumer {
    private static final int MAX_FLUSH_THREADS = 5;
    
    private final String sdkKey;
    private final LDConfig config;
    private final BlockingQueue<EventProcessorMessage> inputChannel;
    private final AtomicInteger flushWorkersActive;
    private final Thread mainThread;
    private final List<Thread> flushWorkerThreads;
    private final BlockingQueue<FlushPayload> payloadQueue;
    private final ArrayList<Event> buffer;
    private final EventSummarizer summarizer;
    private final SimpleLRUCache<String, String> userKeys;
    private final Random random = new Random();
    private final AtomicLong lastKnownPastTime = new AtomicLong(0);
    private final AtomicBoolean disabled = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private boolean capacityExceeded = false;

    private EventConsumer(String sdkKey, LDConfig config,
                          BlockingQueue<EventProcessorMessage> inputChannel,
                          ThreadFactory threadFactory) {
      this.sdkKey = sdkKey;
      this.config = config;
      this.inputChannel = inputChannel;
      this.flushWorkersActive = new AtomicInteger(0);
      this.buffer = new ArrayList<>(config.capacity);
      this.summarizer = new EventSummarizer();
      this.userKeys = new SimpleLRUCache<String, String>(config.userKeysCapacity);

      // This queue only holds one element; it represents a flush task that has not yet been
      // picked up by any worker, so if we try to push another one and are refused, it means
      // all the workers are busy.
      this.payloadQueue = new ArrayBlockingQueue<>(1);
      
      mainThread = threadFactory.newThread(new Runnable() {
        public void run() {
          runMainLoop();
        }
      });
      mainThread.setDaemon(true);
      mainThread.start();
      
      flushWorkerThreads = new ArrayList<>();
      for (int i = 0; i < MAX_FLUSH_THREADS; i++) {
        Thread workerThread = threadFactory.newThread(new Runnable() {
          public void run() {
            runFlushWorker();
          }
        });
        workerThread.setDaemon(true);
        workerThread.start();
        flushWorkerThreads.add(workerThread);
      }
    }
    
    /**
     * This task drains the input queue as quickly as possible. Everything here is done on a single
     * thread so we don't have to synchronize on our internal structures; when it's time to flush,
     * dispatchFlush will fire off another task to do the part that takes longer.
     */
    private void runMainLoop() {
      while (true) {
        try {
          EventProcessorMessage message = inputChannel.take();
          switch(message.type) {
          case EVENT:
            processEvent(message.event);
            break;
          case FLUSH:
            triggerFlush();
            break;
          case FLUSH_USERS:
            userKeys.clear();
            break;
          case SYNC:
            waitUntilAllFlushWorkersInactive();
            message.completed();
            break;
          case SHUTDOWN:
            doShutdown();
            message.completed();
            return;
          }
          message.completed();
        } catch (InterruptedException e) {
        } catch (Exception e) {
          logger.error("Unexpected error in event processor: " + e);
          logger.debug(e.getMessage(), e);
        }
      }
    }
    
    private void doShutdown() {
      waitUntilAllFlushWorkersInactive();
      disabled.set(true); // In case there are any more messages, we want to ignore them
      shuttingDown.set(true);
      for (Thread t: flushWorkerThreads) {
        // Wake up the worker if it's waiting, so it'll notice shuttingDown is true and terminate
        t.interrupt();
      }
      // Note that we don't close the HTTP client here, because it's shared by other components
      // via the LDConfig.  The LDClient will dispose of it.
    }

    private void runFlushWorker() {
      while (!shuttingDown.get()) {
        FlushPayload payload = null;
        try {
          payload = payloadQueue.take();
        } catch (InterruptedException e) {
          continue;
        }
        try {
          doFlush(payload);
        } catch (Exception e) {
          logger.error("Unexpected error in event processor: " + e);
          logger.debug(e.getMessage(), e);
        }        
        flushWorkerFinishedWork();
      }
    }
    
    private void flushWorkerStartingWork() {
      flushWorkersActive.incrementAndGet();
    }
    
    private void flushWorkerFinishedWork() {
      synchronized(flushWorkersActive) {
        flushWorkersActive.decrementAndGet();
        flushWorkersActive.notify();
      }
    }
    
    private void waitUntilAllFlushWorkersInactive() {
      while (true) {
        try {
          synchronized(flushWorkersActive) {
            if (flushWorkersActive.get() == 0) {
              return;
            } else {
              flushWorkersActive.wait();
            }
          }
        } catch (InterruptedException e) {}
      }
    }
    
    private void processEvent(Event e) {
      if (disabled.get()) {
        return;
      }
      
      // For each user we haven't seen before, we add an index event - unless this is already
      // an identify event for that user.
      if (!config.inlineUsersInEvents && e.user != null && !noticeUser(e.user)) {
        if (!(e instanceof IdentifyEvent)) {
          IndexEvent ie = new IndexEvent(e.creationDate, e.user);
          addToBuffer(ie);
        }
      }
      
      // Always record the event in the summarizer.
      summarizer.summarizeEvent(e);

      if (shouldTrackFullEvent(e)) {
        // Sampling interval applies only to fully-tracked events.
        if (config.samplingInterval > 0 && random.nextInt(config.samplingInterval) != 0) {
          return;
        }
        // Queue the event as-is; we'll transform it into an output event when we're flushing
        // (to avoid doing that work on our main thread).
        addToBuffer(e);
      }
    }
    
    // Add to the set of users we've noticed, and return true if the user was already known to us.
    private boolean noticeUser(LDUser user) {
      if (user == null || user.getKey() == null) {
        return false;
      }
      String key = user.getKeyAsString();
      return userKeys.put(key, key) != null;
    }
    
    private void addToBuffer(Event e) {
      if (buffer.size() >= config.capacity) {
        if (!capacityExceeded) { // don't need AtomicBoolean, this is only checked on one thread
          capacityExceeded = true;
          logger.warn("Exceeded event queue capacity. Increase capacity to avoid dropping events.");
        }
      } else {
        capacityExceeded = false;
        buffer.add(e);
      }
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
          // earlier than that point is definitely in the past.  If there's any discrepancy, we
          // want to err on the side of cutting off event debugging sooner.
          long lastPast = lastKnownPastTime.get();
          if (fe.debugEventsUntilDate > lastPast &&
              fe.debugEventsUntilDate > System.currentTimeMillis()) {
            return true;
          }
        }
        return false;
      } else {
        return true;
      }
    }

    private void triggerFlush() {
      if (disabled.get()) {
        return;
      }
      
      Event[] events = buffer.toArray(new Event[buffer.size()]);
      EventSummarizer.EventSummary summary = summarizer.snapshot();
      
      if (events.length != 0 || !summary.isEmpty()) {
        flushWorkerStartingWork();
        FlushPayload payload = new FlushPayload(events, summary);
        if (payloadQueue.offer(payload)) {
          // These events now belong to the next available flush worker, so drop them from the consumer
          buffer.clear();
          summarizer.clear();
        } else {
          logger.debug("Skipped flushing because all workers are busy");
          // All the workers are busy so we can't flush now; keep the events in the consumer
          flushWorkerFinishedWork();
        }
      }
    }
    
    // Runs on a flush worker thread
    private void doFlush(FlushPayload payload) {
      if (payload.events.length == 0 && payload.summary.isEmpty()) {
        return;
      }
      List<EventOutput> eventsOut = new ArrayList<>(payload.events.length + 1);
      for (Event event: payload.events) {
        eventsOut.add(createEventOutput(event));
      }
      if (!payload.summary.isEmpty()) {
        eventsOut.add(createSummaryEvent(payload.summary));
      }

      String json = config.gson.toJson(eventsOut);
      logger.debug("Posting {} event(s) to {} with payload: {}",
          eventsOut.size(), config.eventsURI, json);

      Request request = config.getRequestBuilder(sdkKey)
          .url(config.eventsURI.toString() + "/bulk")
          .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json))
          .addHeader("Content-Type", "application/json")
          .build();

      long startTime = System.currentTimeMillis();
      try (Response response = config.httpClient.newCall(request).execute()) {
        long endTime = System.currentTimeMillis();
        logger.debug("Event delivery took {} ms, response status {}", endTime - startTime, response.code());
        handleResponse(response);
      } catch (IOException e) {
        logger.info("Unhandled exception in LaunchDarkly client when posting events to URL: " + request.url(), e);
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
        return new IndexEventOutput(e.creationDate, e.user);
      } else {
        return null;
      }
    }

    private EventOutput createSummaryEvent(EventSummarizer.EventSummary summary) {
      Map<String, SummaryEventFlag> flagsOut = new HashMap<>();
      for (Map.Entry<CounterKey, CounterValue> entry: summary.counters.entrySet()) {
        SummaryEventFlag fsd = flagsOut.get(entry.getKey().key);
        if (fsd == null) {
          fsd = new SummaryEventFlag(entry.getValue().defaultVal, new ArrayList<SummaryEventCounter>());
          flagsOut.put(entry.getKey().key, fsd);
        }
        SummaryEventCounter c = new SummaryEventCounter(entry.getValue().flagValue,
            entry.getKey().version,
            entry.getValue().count,
            entry.getKey().version == null ? true : null);
        fsd.counters.add(c);
      }
      return new SummaryEventOutput(summary.startDate, summary.endDate, flagsOut);
    }
    
    private void handleResponse(Response response) {
      String dateStr = response.header("Date");
      if (dateStr != null) {
        try {
          lastKnownPastTime.set(HTTP_DATE_FORMAT.parse(dateStr).getTime());
        } catch (ParseException e) {
        }
      }
      if (!response.isSuccessful()) {
        logger.info("Got unexpected response when posting events: " + response);
        if (response.code() == 401) {
          disabled.set(true);
          logger.error("Received 401 error, no further events will be posted since SDK key is invalid");
        }
      }
    }
  }
  
  private static enum MessageType {
    EVENT,
    FLUSH,
    FLUSH_USERS,
    SYNC,
    SHUTDOWN
  }
  
  private static class EventProcessorMessage {
    private final MessageType type;
    private final Event event;
    private final Semaphore reply;
    
    private EventProcessorMessage(MessageType type, Event event, boolean sync) {
      this.type = type;
      this.event = event;
      reply = sync ? new Semaphore(0) : null;
    }
    
    void completed() {
      if (reply != null) {
        reply.release();
      }
    }
    
    void waitForCompletion() {
      if (reply == null) {
        return;
      }
      while (true) {
        try {
          reply.acquire();
          return;
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
  
  private static class FlushPayload {
    final Event[] events;
    final EventSummary summary;
    
    FlushPayload(Event[] events, EventSummary summary) {
      this.events = events;
      this.summary = summary;
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
  
  private static class IndexEvent extends Event {
    IndexEvent(long creationDate, LDUser user) {
      super(creationDate, user);
    }
  }
  
  @SuppressWarnings("unused")
  private static class IndexEventOutput implements EventOutput {
    private final String kind;
    private final long creationDate;
    private final LDUser user;
    
    public IndexEventOutput(long creationDate, LDUser user) {
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
    private final Map<String, SummaryEventFlag> features;
    
    SummaryEventOutput(long startDate, long endDate, Map<String, SummaryEventFlag> features) {
      this.kind = "summary";
      this.startDate = startDate;
      this.endDate = endDate;
      this.features = features;
    }
  }

  private static class SummaryEventFlag {
    @SerializedName("default") final JsonElement defaultVal;
    final List<SummaryEventCounter> counters;
    
    SummaryEventFlag(JsonElement defaultVal, List<SummaryEventCounter> counters) {
      this.defaultVal = defaultVal;
      this.counters = counters;
    }
  }
  
  @SuppressWarnings("unused")
  private static class SummaryEventCounter {
    final JsonElement value;
    final Integer version;
    final int count;
    final Boolean unknown;
    
    SummaryEventCounter(JsonElement value, Integer version, int count, Boolean unknown) {
      this.value = value;
      this.version = version;
      this.count = count;
      this.unknown = unknown;
    }
  }
}
