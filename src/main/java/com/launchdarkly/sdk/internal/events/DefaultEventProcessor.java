package com.launchdarkly.sdk.internal.events;

import com.google.gson.Gson;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogValues;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.internal.events.EventSummarizer.EventSummary;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The internal component that processes and delivers analytics events.
 * <p>
 * This component is not visible to application code; the SDKs may choose to expose an
 * interface for customizing event behavior, but if so, their default implementations of
 * the interface will delegate to this component rather than this component implementing
 * the interface itself. This allows us to make changes as needed to the internal interface
 * and event parameters without disrupting application code, and also to provide internal
 * features that may not be relevant to some SDKs.
 * 
 * The current implementation is really three components. DefaultEventProcessor is a simple
 * facade that accepts event parameters (from SDK activity that might be happening on many
 * threads) and pushes the events onto a queue. The queue is consumed by a single-threaded
 * task run by EventDispatcher, which performs any necessary processing such as
 * incrementing summary counters. When events are ready to deliver, it uses an
 * implementation of EventSender (normally DefaultEventSender) to deliver the JSON data.
 */
public final class DefaultEventProcessor implements Closeable {
  private static final int INITIAL_OUTPUT_BUFFER_SIZE = 2000;

  private static final Gson gson = new Gson();
  
  private final BlockingQueue<EventProcessorMessage> inbox;
  private final ScheduledExecutorService scheduler;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();
  private volatile boolean inputCapacityExceeded = false;
  private final LDLogger logger;

  /**
   * Creates an instance.
   * 
   * @param eventsConfig the events configuration
   * @param sharedExecutor used for scheduling repeating tasks
   * @param threadPriority worker thread priority
   * @param logger the logger
   */
  public DefaultEventProcessor(
      EventsConfiguration eventsConfig,
      ScheduledExecutorService sharedExecutor,
      int threadPriority,
      LDLogger logger
      ) {
    inbox = new ArrayBlockingQueue<>(eventsConfig.capacity);
    
    scheduler = sharedExecutor;
    this.logger = logger;

    new EventDispatcher(
        eventsConfig,
        sharedExecutor,
        threadPriority,
        inbox,
        closed,
        logger
        );
    // we don't need to save a reference to this - we communicate with it entirely through the inbox queue.

    Runnable flusher = postMessageRunnable(MessageType.FLUSH, null);
    scheduledTasks.add(this.scheduler.scheduleAtFixedRate(flusher, eventsConfig.flushIntervalMillis,
        eventsConfig.flushIntervalMillis, TimeUnit.MILLISECONDS));
    if (eventsConfig.contextDeduplicator != null && eventsConfig.contextDeduplicator.getFlushInterval() != null) {
      Runnable userKeysFlusher = postMessageRunnable(MessageType.FLUSH_USERS, null);
      long intervalMillis = eventsConfig.contextDeduplicator.getFlushInterval().longValue();
      scheduledTasks.add(this.scheduler.scheduleAtFixedRate(userKeysFlusher, intervalMillis,
          intervalMillis, TimeUnit.MILLISECONDS));
    }
    if (eventsConfig.diagnosticStore != null) {
      Runnable diagnosticsTrigger = postMessageRunnable(MessageType.DIAGNOSTIC, null);
      scheduledTasks.add(this.scheduler.scheduleAtFixedRate(diagnosticsTrigger, eventsConfig.diagnosticRecordingIntervalMillis,
          eventsConfig.diagnosticRecordingIntervalMillis, TimeUnit.MILLISECONDS));
    }
  }

  /**
   * Enqueues an event.
   * 
   * @param e the input data
   */
  public void sendEvent(Event e) {
    if (!closed.get()) {
      postMessageAsync(MessageType.EVENT, e);
    }
  }

  /**
   * Schedules an asynchronous flush.
   */
  public void flush() {
    if (!closed.get()) {
      postMessageAsync(MessageType.FLUSH, null);
    }
  }

  public void close() throws IOException {
    if (closed.compareAndSet(false, true)) {
      for (ScheduledFuture<?> task: scheduledTasks) {
        task.cancel(false);
      }
      postMessageAsync(MessageType.FLUSH, null);
      postMessageAndWait(MessageType.SHUTDOWN, null);
    }
  }

  void waitUntilInactive() throws IOException { // visible for testing
    postMessageAndWait(MessageType.SYNC, null);
  }

  void postDiagnostic() { // visible for testing
    postMessageAsync(MessageType.DIAGNOSTIC, null);
  }

  private void postMessageAsync(MessageType type, Event event) {
    postToChannel(new EventProcessorMessage(type, event, false));
  }

  private void postMessageAndWait(MessageType type, Event event) {
    EventProcessorMessage message = new EventProcessorMessage(type, event, true);
    if (postToChannel(message)) {
      // COVERAGE: There is no way to reliably cause this to fail in tests
      message.waitForCompletion();
    }
  }

  private Runnable postMessageRunnable(final MessageType messageType, final Event event) {
    return new Runnable() {
      public void run() {
        postMessageAsync(messageType, event);
      }
    };
  }
  
  private boolean postToChannel(EventProcessorMessage message) {
    if (inbox.offer(message)) {
      return true;
    }
    // If the inbox is full, it means the EventDispatcher thread is seriously backed up with not-yet-processed
    // events. This is unlikely, but if it happens, it means the application is probably doing a ton of flag
    // evaluations across many threads-- so if we wait for a space in the inbox, we risk a very serious slowdown
    // of the app. To avoid that, we'll just drop the event. The log warning about this will only be shown once.
    boolean alreadyLogged = inputCapacityExceeded; // possible race between this and the next line, but it's of no real consequence - we'd just get an extra log line
    inputCapacityExceeded = true;
    // COVERAGE: There is no way to reliably cause this condition in tests
    if (!alreadyLogged) {
      logger.warn("Events are being produced faster than they can be processed; some events will be dropped");
    }
    return false;
  }

  private static enum MessageType {
    EVENT,
    FLUSH,
    FLUSH_USERS,
    DIAGNOSTIC,
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
      if (reply == null) { // COVERAGE: there is no way to make this happen from test code
        return;
      }
      while (true) {
        try {
          reply.acquire();
          return;
        }
        catch (InterruptedException ex) { // COVERAGE: there is no way to make this happen from test code.
        }
      }
    }

// intentionally commented out so this doesn't affect coverage reports when we're not debugging
//    @Override
//    public String toString() { // for debugging only
//      return ((event == null) ? type.toString() : (type + ": " + event.getClass().getSimpleName())) +
//          (reply == null ? "" : " (sync)");
//    }
  }

  /**
   * Takes messages from the input queue, updating the event buffer and summary counters
   * on its own thread.
   */
  static final class EventDispatcher {
    private static final int MAX_FLUSH_THREADS = 5;
    private static final int MESSAGE_BATCH_SIZE = 50;

    final EventsConfiguration eventsConfig; // visible for testing
    private final BlockingQueue<EventProcessorMessage> inbox;
    private final AtomicBoolean closed;
    private final List<SendEventsTask> flushWorkers;
    private final AtomicInteger busyFlushWorkersCount;
    private final AtomicLong lastKnownPastTime = new AtomicLong(0);
    private final AtomicBoolean disabled = new AtomicBoolean(false);
    final DiagnosticStore diagnosticStore; // visible for testing
    private final EventContextDeduplicator contextDeduplicator;
    private final ExecutorService sharedExecutor;
    private final SendDiagnosticTaskFactory sendDiagnosticTaskFactory;
    private final LDLogger logger;
    
    private long deduplicatedUsers = 0;

    private EventDispatcher(
        EventsConfiguration eventsConfig,
        ExecutorService sharedExecutor,
        int threadPriority,
        BlockingQueue<EventProcessorMessage> inbox,
        AtomicBoolean closed,
        LDLogger logger
        ) {
      this.eventsConfig = eventsConfig;
      this.inbox = inbox;
      this.closed = closed;
      this.sharedExecutor = sharedExecutor;
      this.diagnosticStore = eventsConfig.diagnosticStore;
      this.busyFlushWorkersCount = new AtomicInteger(0);
      this.logger = logger;

      ThreadFactory threadFactory = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
          Thread t = new Thread(r);
          t.setDaemon(true);;
          t.setName(String.format("LaunchDarkly-event-delivery-%d", t.getId()));
          t.setPriority(threadPriority);
          return t;
        }
      };
      
      // This queue only holds one element; it represents a flush task that has not yet been
      // picked up by any worker, so if we try to push another one and are refused, it means
      // all the workers are busy.
      final BlockingQueue<FlushPayload> payloadQueue = new ArrayBlockingQueue<>(1);

      final EventBuffer outbox = new EventBuffer(eventsConfig.capacity, logger);
      this.contextDeduplicator = eventsConfig.contextDeduplicator;
      
      Thread mainThread = threadFactory.newThread(new Thread() {
        public void run() {
          runMainLoop(inbox, outbox, payloadQueue);
        }
      });
      mainThread.setDaemon(true);

      mainThread.setUncaughtExceptionHandler(this::onUncaughtException);
      
      mainThread.start();

      flushWorkers = new ArrayList<>();
      EventResponseListener listener = this::handleResponse;
      for (int i = 0; i < MAX_FLUSH_THREADS; i++) {
        SendEventsTask task = new SendEventsTask(
            eventsConfig,
            listener,
            payloadQueue,
            busyFlushWorkersCount,
            threadFactory,
            logger
            );
        flushWorkers.add(task);
      }

      if (diagnosticStore != null) {
        // Set up diagnostics
        this.sendDiagnosticTaskFactory = new SendDiagnosticTaskFactory(eventsConfig, this::handleResponse, logger);
        sharedExecutor.submit(sendDiagnosticTaskFactory.createSendDiagnosticTask(diagnosticStore.getInitEvent()));
      } else {
        sendDiagnosticTaskFactory = null;
      }
    }

    private void onUncaughtException(Thread thread, Throwable e) {
      // The thread's main loop catches all exceptions, so we'll only get here if an Error was thrown.
      // In that case, the application is probably already in a bad state, but we can try to degrade
      // relatively gracefully by performing an orderly shutdown of the event processor, so the
      // application won't end up blocking on a queue that's no longer being consumed.
      // COVERAGE: there is no way to make this happen from test code.
      
      logger.error("Event processor thread was terminated by an unrecoverable error. No more analytics events will be sent. {} {}",
          LogValues.exceptionSummary(e), LogValues.exceptionTrace(e));
      // Note that this is a rare case where we always log the exception stacktrace, instead of only
      // logging it at debug level. That's because an exception of this kind should never happen and,
      // if it happens, may be difficult to debug.
      
      // Flip the switch to prevent DefaultEventProcessor from putting any more messages on the queue
      closed.set(true);
      // Now discard everything that was on the queue, but also make sure no one was blocking on a message
      List<EventProcessorMessage> messages = new ArrayList<EventProcessorMessage>();
      inbox.drainTo(messages);
      for (EventProcessorMessage m: messages) {
        m.completed();
      }  
    }
    
    /**
     * This task drains the input queue as quickly as possible. Everything here is done on a single
     * thread so we don't have to synchronize on our internal structures; when it's time to flush,
     * triggerFlush will hand the events off to another task.
     */
    private void runMainLoop(
        BlockingQueue<EventProcessorMessage> inbox,
        EventBuffer outbox,
        BlockingQueue<FlushPayload> payloadQueue
        ) {
      List<EventProcessorMessage> batch = new ArrayList<EventProcessorMessage>(MESSAGE_BATCH_SIZE);
      while (true) {
        try {
          batch.clear();
          batch.add(inbox.take()); // take() blocks until a message is available
          inbox.drainTo(batch, MESSAGE_BATCH_SIZE - 1); // this nonblocking call allows us to pick up more messages if available
          for (EventProcessorMessage message: batch) {
            switch (message.type) { // COVERAGE: adding a default branch does not prevent coverage warnings here due to compiler issues
            case EVENT:
              processEvent(message.event, outbox);
              break;
            case FLUSH:
              triggerFlush(outbox, payloadQueue);
              break;
            case FLUSH_USERS:
              if (contextDeduplicator != null) {
                contextDeduplicator.flush();
              }
              break;
            case DIAGNOSTIC:
              sendAndResetDiagnostics(outbox);
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
        } catch (Exception e) { // COVERAGE: there is no way to cause this condition in tests
          logger.error("Unexpected error in event processor: {}", e.toString());
          logger.debug(e.toString(), e);
        }
      }
    }

    private void sendAndResetDiagnostics(EventBuffer outbox) {
      if (disabled.get()) {
        return;
      }
      long droppedEvents = outbox.getAndClearDroppedCount();
      // We pass droppedEvents and deduplicatedUsers as parameters here because they are updated frequently in the main loop so we want to avoid synchronization on them.
      DiagnosticEvent diagnosticEvent = diagnosticStore.createEventAndReset(droppedEvents, deduplicatedUsers);
      deduplicatedUsers = 0;
      sharedExecutor.submit(sendDiagnosticTaskFactory.createSendDiagnosticTask(diagnosticEvent));
    }

    private void doShutdown() {
      waitUntilAllFlushWorkersInactive();
      disabled.set(true); // In case there are any more messages, we want to ignore them
      for (SendEventsTask task: flushWorkers) {
        task.stop();
      }
      try {
        eventsConfig.eventSender.close();
      } catch (IOException e) {
        logger.error("Unexpected error when closing event sender: {}", LogValues.exceptionSummary(e));
        logger.debug(LogValues.exceptionTrace(e));
      }
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
        } catch (InterruptedException e) {} // COVERAGE: there is no way to cause this condition in tests
      }
    }

    private void processEvent(Event e, EventBuffer outbox) {
      if (disabled.get()) {
        return;
      }

      LDContext context = e.getContext();
      if (context == null) {
        return; // LDClient should never give us an event with no context
      }
      
      // Decide whether to add the event to the payload. Feature events may be added twice, once for
      // the event (if tracked) and once for debugging.
      boolean addIndexEvent = false,
          addFullEvent = false;
      Event debugEvent = null;

      if (e instanceof Event.FeatureRequest) {
        Event.FeatureRequest fe = (Event.FeatureRequest)e;
        outbox.addToSummary(fe);
        addFullEvent = fe.isTrackEvents();
        if (shouldDebugEvent(fe)) {
          debugEvent = fe.toDebugEvent();
        }
      } else {
        addFullEvent = true;
      }

      // For each context we haven't seen before, we add an index event - unless this is already
      // an identify event for that context.
      if (context != null && context.getFullyQualifiedKey() != null) {
        if (e instanceof Event.FeatureRequest || e instanceof Event.Custom) {
          if (contextDeduplicator != null) {
            // Add to the set of contexts we've noticed
            addIndexEvent = contextDeduplicator.processContext(context);
            if (!addIndexEvent) {
              deduplicatedUsers++;
            }
          }
        } else if (e instanceof Event.Identify) {
          if (contextDeduplicator != null) {
            contextDeduplicator.processContext(context); // just mark that we've seen it
          }
        }
      }

      if (addIndexEvent) {
        Event.Index ie = new Event.Index(e.getCreationDate(), e.getContext());
        outbox.add(ie);
      }
      if (addFullEvent) {
        outbox.add(e);
      }
      if (debugEvent != null) {
        outbox.add(debugEvent);
      }
    }

    private boolean shouldDebugEvent(Event.FeatureRequest fe) {
      Long maybeDate = fe.getDebugEventsUntilDate();
      if (maybeDate == null) {
        return false;
      }
      long debugEventsUntilDate = maybeDate.longValue();
      if (debugEventsUntilDate > 0) {
        // The "last known past time" comes from the last HTTP response we got from the server.
        // In case the client's time is set wrong, at least we know that any expiration date
        // earlier than that point is definitely in the past.  If there's any discrepancy, we
        // want to err on the side of cutting off event debugging sooner.
        long lastPast = lastKnownPastTime.get();
        if (debugEventsUntilDate > lastPast &&
            debugEventsUntilDate > System.currentTimeMillis()) {
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
      if (diagnosticStore != null) {
        int eventCount = payload.events.length + (payload.summary.isEmpty() ? 0 : 1);
        diagnosticStore.recordEventsInBatch(eventCount);
      }
      busyFlushWorkersCount.incrementAndGet();
      if (payloadQueue.offer(payload)) {
        // These events now belong to the next available flush worker, so drop them from our state
        outbox.clear();
      } else {
        logger.debug("Skipped flushing because all workers are busy");
        // All the workers are busy so we can't flush now; keep the events in our state
        outbox.summarizer.restoreTo(payload.summary);
        synchronized(busyFlushWorkersCount) {
          busyFlushWorkersCount.decrementAndGet();
          busyFlushWorkersCount.notify();
        }
      }
    }
    
    private void handleResponse(EventSender.Result result) {
      if (result.getTimeFromServer() != null) {
        lastKnownPastTime.set(result.getTimeFromServer().getTime());
      }
      if (result.isMustShutDown()) {
        disabled.set(true);
      }
    }
  }
  
  private static final class EventBuffer {
    final List<Event> events = new ArrayList<>();
    final EventSummarizer summarizer = new EventSummarizer();
    private final int capacity;
    private final LDLogger logger;
    private boolean capacityExceeded = false;
    private long droppedEventCount = 0;

    EventBuffer(int capacity, LDLogger logger) {
      this.capacity = capacity;
      this.logger = logger;
    }

    void add(Event e) {
      if (events.size() >= capacity) {
        if (!capacityExceeded) { // don't need AtomicBoolean, this is only checked on one thread
          capacityExceeded = true;
          logger.warn("Exceeded event queue capacity. Increase capacity to avoid dropping events.");
        }
        droppedEventCount++;
      } else {
        capacityExceeded = false;
        events.add(e);
      }
    }

    void addToSummary(Event.FeatureRequest e) {
      summarizer.summarizeEvent(
          e.getCreationDate(),
          e.getKey(),
          e.getVersion(),
          e.getVariation(),
          e.getValue(),
          e.getDefaultVal(),
          e.getContext()
          );
    }

    boolean isEmpty() {
      return events.isEmpty() && summarizer.isEmpty();
    }

    long getAndClearDroppedCount() {
      long res = droppedEventCount;
      droppedEventCount = 0;
      return res;
    }

    FlushPayload getPayload() {
      Event[] eventsOut = events.toArray(new Event[events.size()]);
      EventSummarizer.EventSummary summary = summarizer.getSummaryAndReset();
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
    void handleResponse(EventSender.Result result);
  }

  private static final class SendEventsTask implements Runnable {
    private final EventsConfiguration eventsConfig;
    private final EventResponseListener responseListener;
    private final BlockingQueue<FlushPayload> payloadQueue;
    private final AtomicInteger activeFlushWorkersCount;
    private final AtomicBoolean stopping;
    private final EventOutputFormatter formatter;
    private final Thread thread;
    private final LDLogger logger;

    SendEventsTask(
        EventsConfiguration eventsConfig,
        EventResponseListener responseListener,
        BlockingQueue<FlushPayload> payloadQueue,
        AtomicInteger activeFlushWorkersCount,
        ThreadFactory threadFactory,
        LDLogger logger
        ) {
      this.eventsConfig = eventsConfig;
      this.formatter = new EventOutputFormatter(eventsConfig);
      this.responseListener = responseListener;
      this.payloadQueue = payloadQueue;
      this.activeFlushWorkersCount = activeFlushWorkersCount;
      this.stopping = new AtomicBoolean(false);
      this.logger = logger;
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
          ByteArrayOutputStream buffer = new ByteArrayOutputStream(INITIAL_OUTPUT_BUFFER_SIZE);
          Writer writer = new BufferedWriter(new OutputStreamWriter(buffer, Charset.forName("UTF-8")), INITIAL_OUTPUT_BUFFER_SIZE);
          int outputEventCount = formatter.writeOutputEvents(payload.events, payload.summary, writer);
          writer.flush();
          EventSender.Result result = eventsConfig.eventSender.sendAnalyticsEvents(
              buffer.toByteArray(),
              outputEventCount,
              eventsConfig.eventsUri
              );
          responseListener.handleResponse(result);
        } catch (Exception e) {
          logger.error("Unexpected error in event processor: {}", LogValues.exceptionSummary(e));
          logger.debug(LogValues.exceptionTrace(e));
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
  }

  private static final class SendDiagnosticTaskFactory {
    private final EventsConfiguration eventsConfig;
    private final EventResponseListener eventResponseListener;
    private final LDLogger logger;

    SendDiagnosticTaskFactory(
        EventsConfiguration eventsConfig,
        EventResponseListener eventResponseListener,
        LDLogger logger
        ) {
      this.eventsConfig = eventsConfig;
      this.eventResponseListener = eventResponseListener;
      this.logger = logger;
    }

    Runnable createSendDiagnosticTask(final DiagnosticEvent diagnosticEvent) {
      return new Runnable() {
        @Override
        public void run() {
          try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(INITIAL_OUTPUT_BUFFER_SIZE);
            Writer writer = new BufferedWriter(new OutputStreamWriter(buffer, Charset.forName("UTF-8")), INITIAL_OUTPUT_BUFFER_SIZE);
            gson.toJson(diagnosticEvent, writer);
            writer.flush();
            EventSender.Result result = eventsConfig.eventSender.sendDiagnosticEvent(
                buffer.toByteArray(), eventsConfig.eventsUri);
            if (eventResponseListener != null) {
              eventResponseListener.handleResponse(result);
            }
          } catch (Exception e) {
            logger.error("Unexpected error in event processor: {}", e.toString());
            logger.debug(e.toString(), e);
          }
        }
      };
    }
  }
}
