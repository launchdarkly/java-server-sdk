package com.launchdarkly.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.launchdarkly.client.EventSummarizer.EventSummary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.launchdarkly.client.Util.getRequestBuilder;
import static com.launchdarkly.client.Util.httpErrorMessage;
import static com.launchdarkly.client.Util.isHttpErrorRecoverable;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class DefaultEventProcessor implements EventProcessor {
  private static final Logger logger = LoggerFactory.getLogger(DefaultEventProcessor.class);
  private static final String EVENT_SCHEMA_HEADER = "X-LaunchDarkly-Event-Schema";
  private static final String EVENT_SCHEMA_VERSION = "3";
  
  private final BlockingQueue<EventProcessorMessage> inbox;
  private final ScheduledExecutorService scheduler;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private volatile boolean inputCapacityExceeded = false;
  
  DefaultEventProcessor(String sdkKey, LDConfig config) {
    inbox = new ArrayBlockingQueue<>(config.capacity);

    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat("LaunchDarkly-EventProcessor-%d")
        .setPriority(Thread.MIN_PRIORITY)
        .build();
    scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);

    new EventDispatcher(sdkKey, config, inbox, threadFactory, closed);

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
    postMessageAndWait(MessageType.SYNC, null);
  }
  
  private void postMessageAsync(MessageType type, Event event) {
    postToChannel(new EventProcessorMessage(type, event, false));
  }
  
  private void postMessageAndWait(MessageType type, Event event) {
    EventProcessorMessage message = new EventProcessorMessage(type, event, true);
    if (postToChannel(message)) {
      message.waitForCompletion();
    }
  }
  
  private boolean postToChannel(EventProcessorMessage message) {
    if (inbox.offer(message)) {
      return true;
    }
    // If the inbox is full, it means the EventDispatcher thread is seriously backed up with not-yet-processed
    // events. This is unlikely, but if it happens, it means the application is probably doing a ton of flag
    // evaluations across many threads-- so if we wait for a space in the inbox, we risk a very serious slowdown
    // of the app. To avoid that, we'll just drop the event. The log warning about this will only be shown once.
    boolean alreadyLogged = inputCapacityExceeded;
    inputCapacityExceeded = true;
    if (!alreadyLogged) {
      logger.warn("Events are being produced faster than they can be processed; some events will be dropped");
    }
    return false;
  }

  private static enum MessageType {
    EVENT,
    FLUSH,
    FLUSH_USERS,
    SYNC,
    SHUTDOWN
  }
  
  private static final class EventProcessorMessage {
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
    public String toString() { // for debugging only
      return ((event == null) ? type.toString() : (type + ": " + event.getClass().getSimpleName())) +
          (reply == null ? "" : " (sync)");
    }
  }
  
  /**
   * Takes messages from the input queue, updating the event buffer and summary counters
   * on its own thread.
   */
  static final class EventDispatcher {
    private static final int MAX_FLUSH_THREADS = 5;
    private static final int MESSAGE_BATCH_SIZE = 50;
    static final SimpleDateFormat HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    
    private final LDConfig config;
    private final List<SendEventsTask> flushWorkers;
    private final AtomicInteger busyFlushWorkersCount;
    private final Random random = new Random();
    private final AtomicLong lastKnownPastTime = new AtomicLong(0);
    private final AtomicBoolean disabled = new AtomicBoolean(false);

    private EventDispatcher(String sdkKey, LDConfig config,
                            final BlockingQueue<EventProcessorMessage> inbox,
                            ThreadFactory threadFactory,
                            final AtomicBoolean closed) {
      this.config = config;
      this.busyFlushWorkersCount = new AtomicInteger(0);

      // This queue only holds one element; it represents a flush task that has not yet been
      // picked up by any worker, so if we try to push another one and are refused, it means
      // all the workers are busy.
      final BlockingQueue<FlushPayload> payloadQueue = new ArrayBlockingQueue<>(1);
      
      final EventBuffer outbox = new EventBuffer(config.capacity);
      final SimpleLRUCache<String, String> userKeys = new SimpleLRUCache<String, String>(config.userKeysCapacity);
      
      Thread mainThread = threadFactory.newThread(new Runnable() {
        public void run() {
          runMainLoop(inbox, outbox, userKeys, payloadQueue);
        }
      });
      mainThread.setDaemon(true);
      
      mainThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
        // The thread's main loop catches all exceptions, so we'll only get here if an Error was thrown.
        // In that case, the application is probably already in a bad state, but we can try to degrade
        // relatively gracefully by performing an orderly shutdown of the event processor, so the
        // application won't end up blocking on a queue that's no longer being consumed.
        public void uncaughtException(Thread t, Throwable e) {
          logger.error("Event processor thread was terminated by an unrecoverable error. No more analytics events will be sent.", e);
          // Flip the switch to prevent DefaultEventProcessor from putting any more messages on the queue
          closed.set(true);
          // Now discard everything that was on the queue, but also make sure no one was blocking on a message
          List<EventProcessorMessage> messages = new ArrayList<EventProcessorMessage>();
          inbox.drainTo(messages);
          for (EventProcessorMessage m: messages) {
            m.completed();
          }
        }
      });
      
      mainThread.start();
      
      flushWorkers = new ArrayList<>();
      EventResponseListener listener = new EventResponseListener() {
          public void handleResponse(Response response) {
            EventDispatcher.this.handleResponse(response);
          }
        };
      for (int i = 0; i < MAX_FLUSH_THREADS; i++) {
        SendEventsTask task = new SendEventsTask(sdkKey, config, listener, payloadQueue,
            busyFlushWorkersCount, threadFactory);
        flushWorkers.add(task);
      }
    }
    
    /**
     * This task drains the input queue as quickly as possible. Everything here is done on a single
     * thread so we don't have to synchronize on our internal structures; when it's time to flush,
     * triggerFlush will hand the events off to another task.
     */
    private void runMainLoop(BlockingQueue<EventProcessorMessage> inbox,
        EventBuffer outbox, SimpleLRUCache<String, String> userKeys,
        BlockingQueue<FlushPayload> payloadQueue) {
      List<EventProcessorMessage> batch = new ArrayList<EventProcessorMessage>(MESSAGE_BATCH_SIZE);
      while (true) {
        try {
          batch.clear();
          batch.add(inbox.take()); // take() blocks until a message is available
          inbox.drainTo(batch, MESSAGE_BATCH_SIZE - 1); // this nonblocking call allows us to pick up more messages if available
          for (EventProcessorMessage message: batch) {
            switch(message.type) {
            case EVENT:
              processEvent(message.event, userKeys, outbox);
              break;
            case FLUSH:
              triggerFlush(outbox, payloadQueue);
              break;
            case FLUSH_USERS:
              userKeys.clear();
              break;
            case SYNC: // this is used only by unit tests
              waitUntilAllFlushWorkersInactive();
              break;
            case SHUTDOWN:
              doShutdown();
              message.completed();
              return; // deliberately exit the thread loop
            }
            message.completed();
          }
        } catch (InterruptedException e) {
        } catch (Exception e) {
          logger.error("Unexpected error in event processor: {}", e.toString());
          logger.debug(e.toString(), e);
        }
      }
    }
    
    private void doShutdown() {
      waitUntilAllFlushWorkersInactive();
      disabled.set(true); // In case there are any more messages, we want to ignore them
      for (SendEventsTask task: flushWorkers) {
        task.stop();
      }
      // Note that we don't close the HTTP client here, because it's shared by other components
      // via the LDConfig.  The LDClient will dispose of it.
    }

    private void waitUntilAllFlushWorkersInactive() {
      while (true) {
        try {
          synchronized(busyFlushWorkersCount) {
            if (busyFlushWorkersCount.get() == 0) {
              return;
            } else {
              busyFlushWorkersCount.wait();
            }
          }
        } catch (InterruptedException e) {}
      }
    }
    
    private void processEvent(Event e, SimpleLRUCache<String, String> userKeys, EventBuffer outbox) {
      if (disabled.get()) {
        return;
      }

      // Always record the event in the summarizer.
      outbox.addToSummary(e);

      // Decide whether to add the event to the payload. Feature events may be added twice, once for
      // the event (if tracked) and once for debugging.
      boolean addIndexEvent = false,
          addFullEvent = false;
      Event debugEvent = null;
      
      if (e instanceof Event.FeatureRequest) {
        if (shouldSampleEvent()) {
          Event.FeatureRequest fe = (Event.FeatureRequest)e;
          addFullEvent = fe.trackEvents;
          if (shouldDebugEvent(fe)) {
            debugEvent = EventFactory.DEFAULT.newDebugEvent(fe);
          }
        }
      } else {
        addFullEvent = shouldSampleEvent();
      }
      
      // For each user we haven't seen before, we add an index event - unless this is already
      // an identify event for that user.
      if (!addFullEvent || !config.inlineUsersInEvents) {
        if (e.user != null && e.user.getKey() != null && !noticeUser(e.user, userKeys)) {
          if (!(e instanceof Event.Identify)) {
            addIndexEvent = true;
          }          
        }
      }
      
      if (addIndexEvent) {
        Event.Index ie = new Event.Index(e.creationDate, e.user);
        outbox.add(ie);
      }
      if (addFullEvent) {
        outbox.add(e);
      }
      if (debugEvent != null) {
        outbox.add(debugEvent);
      }
    }
    
    // Add to the set of users we've noticed, and return true if the user was already known to us.
    private boolean noticeUser(LDUser user, SimpleLRUCache<String, String> userKeys) {
      if (user == null || user.getKey() == null) {
        return false;
      }
      String key = user.getKeyAsString();
      return userKeys.put(key, key) != null;
    }
    
    private boolean shouldSampleEvent() {
      return config.samplingInterval <= 0 || random.nextInt(config.samplingInterval) == 0;
    }
    
    private boolean shouldDebugEvent(Event.FeatureRequest fe) {
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
    }
    
    private void triggerFlush(EventBuffer outbox, BlockingQueue<FlushPayload> payloadQueue) {
      if (disabled.get() || outbox.isEmpty()) {
        return;
      }
      FlushPayload payload = outbox.getPayload();
      busyFlushWorkersCount.incrementAndGet();
      if (payloadQueue.offer(payload)) {
        // These events now belong to the next available flush worker, so drop them from our state
        outbox.clear();
      } else {
        logger.debug("Skipped flushing because all workers are busy");
        // All the workers are busy so we can't flush now; keep the events in our state
        synchronized(busyFlushWorkersCount) {
          busyFlushWorkersCount.decrementAndGet();
          busyFlushWorkersCount.notify();
        }
      }
    }
    
    private void handleResponse(Response response) {
      String dateStr = response.header("Date");
      if (dateStr != null) {
        try {
          lastKnownPastTime.set(HTTP_DATE_FORMAT.parse(dateStr).getTime());
        } catch (ParseException e) {
        }
      }
      if (!isHttpErrorRecoverable(response.code())) {
        disabled.set(true);
        logger.error(httpErrorMessage(response.code(), "posting events", "some events were dropped"));
        // It's "some events were dropped" because we're not going to retry *this* request any more times -
        // we only get to this point if we have used up our retry attempts. So the last batch of events was
        // lost, even though we will still try to post *other* events in the future.
      }
    }
  }
  
  private static final class EventBuffer {
    final List<Event> events = new ArrayList<>();
    final EventSummarizer summarizer = new EventSummarizer();
    private final int capacity;
    private boolean capacityExceeded = false;
    
    EventBuffer(int capacity) {
      this.capacity = capacity;
    }
    
    void add(Event e) {
      if (events.size() >= capacity) {
        if (!capacityExceeded) { // don't need AtomicBoolean, this is only checked on one thread
          capacityExceeded = true;
          logger.warn("Exceeded event queue capacity. Increase capacity to avoid dropping events.");
        }
      } else {
        capacityExceeded = false;
        events.add(e);
      }
    }
    
    void addToSummary(Event e) {
      summarizer.summarizeEvent(e);
    }
    
    boolean isEmpty() {
      return events.isEmpty() && summarizer.snapshot().isEmpty();
    }
    
    FlushPayload getPayload() {
      Event[] eventsOut = events.toArray(new Event[events.size()]);
      EventSummarizer.EventSummary summary = summarizer.snapshot();
      return new FlushPayload(eventsOut, summary);
    }
    
    void clear() {
      events.clear();
      summarizer.clear();
    }
  }
  
  private static final class FlushPayload {
    final Event[] events;
    final EventSummary summary;
    
    FlushPayload(Event[] events, EventSummary summary) {
      this.events = events;
      this.summary = summary;
    }
  }
  
  private static interface EventResponseListener {
    void handleResponse(Response response);
  }
  
  private static final class SendEventsTask implements Runnable {
    private final String sdkKey;
    private final LDConfig config;
    private final EventResponseListener responseListener;
    private final BlockingQueue<FlushPayload> payloadQueue;
    private final AtomicInteger activeFlushWorkersCount;
    private final AtomicBoolean stopping;
    private final EventOutput.Formatter formatter;
    private final Thread thread;
    
    SendEventsTask(String sdkKey, LDConfig config, EventResponseListener responseListener,
                   BlockingQueue<FlushPayload> payloadQueue, AtomicInteger activeFlushWorkersCount,
                   ThreadFactory threadFactory) {
      this.sdkKey = sdkKey;
      this.config = config;
      this.formatter = new EventOutput.Formatter(config.inlineUsersInEvents);
      this.responseListener = responseListener;
      this.payloadQueue = payloadQueue;
      this.activeFlushWorkersCount = activeFlushWorkersCount;
      this.stopping = new AtomicBoolean(false);
      thread = threadFactory.newThread(this);
      thread.setDaemon(true);
      thread.start();
    }
    
    public void run() {
      while (!stopping.get()) {
        FlushPayload payload = null;
        try {
          payload = payloadQueue.take();
        } catch (InterruptedException e) {
          continue;
        }
        try {
          List<EventOutput> eventsOut = formatter.makeOutputEvents(payload.events, payload.summary);
          if (!eventsOut.isEmpty()) {
            postEvents(eventsOut);
          }
        } catch (Exception e) {
          logger.error("Unexpected error in event processor: {}", e.toString());
          logger.debug(e.toString(), e);
        }
        synchronized (activeFlushWorkersCount) {
          activeFlushWorkersCount.decrementAndGet();
          activeFlushWorkersCount.notifyAll();
        }
      }
    }
    
    void stop() {
      stopping.set(true);
      thread.interrupt();
    }
    
    private void postEvents(List<EventOutput> eventsOut) {
      String json = config.gson.toJson(eventsOut);
      String uriStr = config.eventsURI.toString() + "/bulk";
      
      logger.debug("Posting {} event(s) to {} with payload: {}",
          eventsOut.size(), uriStr, json);

      for (int attempt = 0; attempt < 2; attempt++) {
        if (attempt > 0) {
          logger.warn("Will retry posting events after 1 second");
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {}
        }
        Request request = getRequestBuilder(sdkKey)
            .url(uriStr)
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json))
            .addHeader("Content-Type", "application/json")
            .addHeader(EVENT_SCHEMA_HEADER, EVENT_SCHEMA_VERSION)
            .build();
  
        long startTime = System.currentTimeMillis();
        try (Response response = config.httpClient.newCall(request).execute()) {
          long endTime = System.currentTimeMillis();
          logger.debug("Event delivery took {} ms, response status {}", endTime - startTime, response.code());
          if (!response.isSuccessful()) {
            logger.warn("Unexpected response status when posting events: {}", response.code());
            if (isHttpErrorRecoverable(response.code())) {
              continue;
            }
          }
          responseListener.handleResponse(response);
          break;
        } catch (IOException e) {
          logger.warn("Unhandled exception in LaunchDarkly client when posting events to URL: " + request.url(), e);
          continue;
        }
      }
    }
  }
}
