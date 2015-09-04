package com.launchdarkly.client;

import org.glassfish.jersey.internal.util.collection.StringKeyIgnoreCaseMultivaluedMap;
import org.glassfish.jersey.media.sse.*;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

// EventSource class modified from
// https://github.com/jersey/jersey/blob/master/media/sse/src/main/java/org/glassfish/jersey/media/sse/EventSource.java
// Modifications:
// - support for custom headers
// - set spawned thread as a daemon to permit application shutdown
public class EventSource implements EventListener {

  /**
   * Default SSE {@link EventSource} reconnect delay value in milliseconds.
   *
   * @since 2.3
   */
  public static final long RECONNECT_DEFAULT = 500;

  private static enum State {
    READY, OPEN, CLOSED
  }

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(EventSource.class);

  /**
   * SSE streaming resource target.
   */
  private final WebTarget target;
  /**
   * Default reconnect delay.
   */
  private final long reconnectDelay;
  /**
   * Flag indicating if the persistent HTTP connections should be disabled.
   */
  private final boolean disableKeepAlive;
  /**
   * Incoming SSE event processing task executor.
   */
  private final ScheduledExecutorService executor;
  /**
   * Event source internal state.
   */
  private final AtomicReference<State> state = new AtomicReference<State>(State.READY);
  /**
   * List of all listeners not bound to receive only events of a particular name.
   */
  private final List<EventListener> unboundListeners = new CopyOnWriteArrayList<EventListener>();
  /**
   * A map of listeners bound to receive only events of a particular name.
   */
  private final ConcurrentMap<String, List<EventListener>> boundListeners = new ConcurrentHashMap<String, List<EventListener>>();

  private final MultivaluedMap<String, Object> headers;

  /**
   * Jersey {@link EventSource} builder class.
   *
   * Event source builder provides methods that let you conveniently configure and subsequently build
   * a new {@code EventSource} instance. You can obtain a new event source builder instance using
   * a static {@link EventSource#target(javax.ws.rs.client.WebTarget) EventSource.target(endpoint)} factory method.
   * <p>
   * For example:
   * </p>
   * <pre>
   * EventSource es = EventSource.target(endpoint).named("my source")
   *                             .reconnectingEvery(5, SECONDS)
   *                             .open();
   * </pre>
   *
   * @since 2.3
   */
  public static class Builder {

    private final WebTarget endpoint;

    private long reconnect = EventSource.RECONNECT_DEFAULT;
    private String name = null;
    private boolean disableKeepAlive = true;

    private Builder(final WebTarget endpoint) {
      this.endpoint = endpoint;
    }

    private MultivaluedMap<String, Object> headers = new StringKeyIgnoreCaseMultivaluedMap<Object>();

    /**
     * Set a custom name for the event source.
     * <p>
     * At present, custom event source name is mainly useful to be able to distinguish different event source
     * event processing threads from one another. If not set, a default name will be generated using the
     * SSE endpoint URI.
     * </p>
     *
     * @param name custom event source name.
     * @return updated event source builder instance.
     */
    public Builder named(String name) {
      this.name = name;
      return this;
    }

    /**
     * Instruct event source to use
     * <a href="http://en.wikipedia.org/wiki/HTTP_persistent_connection">persistent HTTP connections</a> when connecting
     * (or reconnecting) to the SSE endpoint, provided the mechanism is supported by the underlying client
     * {@link org.glassfish.jersey.client.spi.Connector}.
     * <p>
     * By default, the persistent HTTP connections are disabled for the reasons discussed in the {@link EventSource}
     * javadoc.
     * </p>
     *
     * @return updated event source builder instance.
     */
    public Builder usePersistentConnections() {
      disableKeepAlive = false;
      return this;
    }

    public Builder header(String name, Object value) {
      if (value == null) {
        headers.remove(name);
      } else {
        headers.add(name, value);
      }
      return this;
    }

    /**
     * Set the initial reconnect delay to be used by the event source.
     * <p>
     * Note that this value may be later overridden by the SSE endpoint using either a {@code retry} SSE event field
     * or <tt>HTTP 503 + {@value javax.ws.rs.core.HttpHeaders#RETRY_AFTER}</tt> mechanism as described
     * in the {@link EventSource} javadoc.
     * </p>
     *
     * @param delay the default time to wait before attempting to recover from a connection loss.
     * @param unit  time unit of the reconnect delay parameter.
     * @return updated event source builder instance.
     */
    public Builder reconnectingEvery(final long delay, TimeUnit unit) {
      reconnect = unit.toMillis(delay);
      return this;
    }

    /**
     * Build new SSE event source pointing at a SSE streaming {@link WebTarget web target}.
     * <p>
     * The returned event source is ready, but not {@link EventSource#open() connected} to the SSE endpoint.
     * It is expected that you will manually invoke its {@link #open()} method once you are ready to start
     * receiving SSE events. In case you want to build an event source instance that is already connected
     * to the SSE endpoint, use the event source builder {@link #open()} method instead.
     * </p>
     * <p>
     * Once the event source is open, the incoming events are processed by the event source in an
     * asynchronous task that runs in an internal single-threaded {@link ScheduledExecutorService
     * scheduled executor service}.
     * </p>
     *
     * @return new event source instance, ready to be connected to the SSE endpoint.
     * @see #open()
     */
    public EventSource build() {
      return new EventSource(endpoint, name, reconnect, disableKeepAlive, false, headers);
    }

    /**
     * Build new SSE event source pointing at a SSE streaming {@link WebTarget web target}.
     * <p>
     * The returned event source is already {@link EventSource#open() connected} to the SSE endpoint
     * and is processing any new incoming events. In case you want to build an event source instance
     * that is already ready, but not automatically connected to the SSE endpoint, use the event source
     * builder {@link #build()} method instead.
     * </p>
     * <p>
     * The incoming events are processed by the event source in an asynchronous task that runs in an
     * internal single-threaded {@link ScheduledExecutorService scheduled executor service}.
     * </p>
     *
     * @return new event source instance, already connected to the SSE endpoint.
     * @see #build()
     */
    public EventSource open() {
      // opening directly in the constructor is just plain ugly...
      final EventSource source = new EventSource(endpoint, name, reconnect, disableKeepAlive, false, headers);
      source.open();
      return source;
    }
  }

  /**
   * Create a new {@link EventSource.Builder event source builder} that provides convenient way how to
   * configure and fine-tune various aspects of a newly prepared event source instance.
   *
   * @param endpoint SSE streaming endpoint. Must not be {@code null}.
   * @return a builder of a new event source instance pointing at the specified SSE streaming endpoint.
   * @throws NullPointerException in case the supplied web target is {@code null}.
   * @since 2.3
   */
  public static Builder target(WebTarget endpoint) {
    return new Builder(endpoint);
  }

  /**
   * Create new SSE event source and open a connection it to the supplied SSE streaming {@link WebTarget web target}.
   *
   * This constructor is performs the same series of actions as a call to:
   * <pre>EventSource.target(endpoint).open()</pre>
   * <p>
   * The created event source instance automatically {@link #open opens a connection} to the supplied SSE streaming
   * web target and starts processing incoming {@link org.glassfish.jersey.media.sse.InboundEvent events}.
   * </p>
   * <p>
   * The incoming events are processed by the event source in an asynchronous task that runs in an
   * internal single-threaded {@link ScheduledExecutorService scheduled executor service}.
   * </p>
   *
   * @param endpoint SSE streaming endpoint. Must not be {@code null}.
   * @throws NullPointerException in case the supplied web target is {@code null}.
   */
  public EventSource(final WebTarget endpoint) {
    this(endpoint, true, new StringKeyIgnoreCaseMultivaluedMap<Object>());
  }

  /**
   * Create new SSE event source pointing at a SSE streaming {@link WebTarget web target}.
   *
   * This constructor is performs the same series of actions as a call to:
   * <pre>
   * if (open) {
   *     EventSource.target(endpoint).open();
   * } else {
   *     EventSource.target(endpoint).build();
   * }</pre>
   * <p>
   * If the supplied {@code open} flag is {@code true}, the created event source instance automatically
   * {@link #open opens a connection} to the supplied SSE streaming web target and starts processing incoming
   * {@link org.glassfish.jersey.media.sse.InboundEvent events}.
   * Otherwise, if the {@code open} flag is set to {@code false}, the created event source instance
   * is not automatically connected to the web target. In this case it is expected that the user who
   * created the event source will manually invoke its {@link #open()} method.
   * </p>
   * <p>
   * Once the event source is open, the incoming events are processed by the event source in an
   * asynchronous task that runs in an internal single-threaded {@link ScheduledExecutorService
   * scheduled executor service}.
   * </p>
   *
   * @param endpoint SSE streaming endpoint. Must not be {@code null}.
   * @param open     if {@code true}, the event source will immediately connect to the SSE endpoint,
   *                 if {@code false}, the connection will not be established until {@link #open()} method is
   *                 called explicitly on the event stream.
   * @throws NullPointerException in case the supplied web target is {@code null}.
   */
  public EventSource(final WebTarget endpoint, final boolean open, final MultivaluedMap<String, Object> headers) {
    this(endpoint, null, RECONNECT_DEFAULT, true, open, headers);
  }

  private EventSource(final WebTarget target,
                      final String name,
                      final long reconnectDelay,
                      final boolean disableKeepAlive,
                      final boolean open,
                      final MultivaluedMap<String, Object> headers) {
    if (target == null) {
      throw new NullPointerException("Web target is 'null'.");
    }
    this.target = target; // SseFeature.register(target);
    this.reconnectDelay = reconnectDelay;
    this.disableKeepAlive = disableKeepAlive;

    final String esName = (name == null) ? createDefaultName(target) : name;
    this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(r, esName);
        t.setDaemon(true);
        return t;
      }
    });

    this.headers = new StringKeyIgnoreCaseMultivaluedMap<Object>();
    this.headers.putAll(headers);

    if (open) {
      open();
    }
  }

  private static String createDefaultName(WebTarget target) {
    return String.format("jersey-sse-event-source-[%s]", target.getUri().toASCIIString());
  }

  /**
   * Open the connection to the supplied SSE underlying {@link WebTarget web target} and start processing incoming
   * {@link org.glassfish.jersey.media.sse.InboundEvent events}.
   *
   * @throws IllegalStateException in case the event source has already been opened earlier.
   */
  public void open() {
    if (!state.compareAndSet(State.READY, State.OPEN)) {
      switch (state.get()) {
        case OPEN:
          throw new IllegalStateException(LocalizationMessages.EVENT_SOURCE_ALREADY_CONNECTED());
        case CLOSED:
          throw new IllegalStateException(LocalizationMessages.EVENT_SOURCE_ALREADY_CLOSED());
      }
    }

    EventProcessor processor = new EventProcessor(reconnectDelay, null, headers);
    executor.submit(processor);

    // return only after the first request to the SSE endpoint has been made
    //processor.awaitFirstContact();
  }

  /**
   * Check if this event source instance has already been {@link #open() opened}.
   *
   * @return {@code true} if this event source is open, {@code false} otherwise.
   */
  public boolean isOpen() {
    return state.get() == State.OPEN;
  }

  /**
   * Register new {@link EventListener event listener} to receive all streamed {@link org.glassfish.jersey.media.sse.InboundEvent SSE events}.
   *
   * @param listener event listener to be registered with the event source.
   * @see #register(EventListener, String, String...)
   */
  public void register(final EventListener listener) {
    register(listener, null);
  }

  /**
   * Add name-bound {@link EventListener event listener} which will be called only for incoming SSE
   * {@link org.glassfish.jersey.media.sse.InboundEvent events} whose {@link org.glassfish.jersey.media.sse.InboundEvent#getName() name} is equal to the specified
   * name(s).
   *
   * @param listener   event listener to register with this event source.
   * @param eventName  inbound event name.
   * @param eventNames additional event names.
   * @see #register(EventListener)
   */
  public void register(final EventListener listener, final String eventName, final String... eventNames) {
    if (eventName == null) {
      unboundListeners.add(listener);
    } else {
      addBoundListener(eventName, listener);

      if (eventNames != null) {
        for (String name : eventNames) {
          addBoundListener(name, listener);
        }
      }
    }
  }

  private void addBoundListener(final String name, final EventListener listener) {
    List<EventListener> listeners = boundListeners.putIfAbsent(name,
        new CopyOnWriteArrayList<EventListener>(Collections.singleton(listener)));
    if (listeners != null) {
      // alas, new listener collection registration conflict:
      // need to add the new listener to the existing listener collection
      listeners.add(listener);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default {@code EventSource} implementation is empty, users can override this method to handle
   * incoming {@link org.glassfish.jersey.media.sse.InboundEvent}s.
   * </p>
   * <p>
   * Note that overriding this method may be necessary to make sure no {@code InboundEvent incoming events}
   * are lost in case the event source is constructed using {@link #EventSource(javax.ws.rs.client.WebTarget)}
   * constructor or in case a {@code true} flag is passed to the {@link #EventSource(javax.ws.rs.client.WebTarget, boolean, javax.ws.rs.core.MultivaluedMap)}
   * constructor, since the connection is opened as as part of the constructor call and the event processing starts
   * immediately. Therefore any {@link EventListener}s registered later after the event source has been constructed
   * may miss the notifications about the one or more events that arrive immediately after the connection to the
   * event source is established.
   * </p>
   *
   * @param inboundEvent received inbound event.
   */
  @Override
  public void onEvent(final InboundEvent inboundEvent) {
    // do nothing
  }

  /**
   * Close this event source.
   *
   * The method will wait up to 5 seconds for the internal event processing task to complete.
   */
  public void close() {
    close(5, TimeUnit.SECONDS);
  }

  /**
   * Close this event source and wait for the internal event processing task to complete
   * for up to the specified amount of wait time.
   * <p>
   * The method blocks until the event processing task has completed execution after a shutdown
   * request, or until the timeout occurs, or the current thread is interrupted, whichever happens
   * first.
   * </p>
   * <p>
   * In case the waiting for the event processing task has been interrupted, this method restores
   * the {@link Thread#interrupted() interrupt} flag on the thread before returning {@code false}.
   * </p>
   *
   * @param timeout the maximum time to wait.
   * @param unit    the time unit of the timeout argument.
   * @return {@code true} if this executor terminated and {@code false} if the timeout elapsed
   * before termination or the termination was interrupted.
   */
  public boolean close(final long timeout, final TimeUnit unit) {
    shutdown();
    try {
      if (!executor.awaitTermination(timeout, unit)) {
        return false;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
    return true;
  }

  private void shutdown() {
    if (state.getAndSet(State.CLOSED) != State.CLOSED) {
      executor.shutdownNow();
    }
  }

  /**
   * Private event processor task responsible for connecting to the SSE stream and processing
   * incoming SSE events as well as handling any connection issues.
   */
  private class EventProcessor implements Runnable, EventListener {

    /**
     * Open connection response arrival synchronization latch.
     */
    private final CountDownLatch firstContactSignal;
    private final MultivaluedMap<String, Object> headers;
    /**
     * Last received event id.
     */
    private String lastEventId;
    /**
     * Re-connect delay.
     */
    private long reconnectDelay;

    public EventProcessor(final long reconnectDelay, final String lastEventId, final MultivaluedMap<String, Object> headers) {
      /**
       * Synchronization barrier used to signal that the initial contact with SSE endpoint
       * has been made.
       */
      this.firstContactSignal = new CountDownLatch(1);

      this.reconnectDelay = reconnectDelay;
      this.lastEventId = lastEventId;
      this.headers = headers;
    }

    private EventProcessor(final EventProcessor that) {
      this.firstContactSignal = null;

      this.reconnectDelay = that.reconnectDelay;
      this.lastEventId = that.lastEventId;
      this.headers = that.headers;
    }

    @Override
    public void run() {
      logger.debug("Listener task started.");

      EventInput eventInput = null;
      try {
        try {
          final Invocation.Builder request = prepareHandshakeRequest();
          if (state.get() == State.OPEN) { // attempt to connect only if even source is open
            logger.debug("Connecting...");
            eventInput = request.get(EventInput.class);
            logger.debug("Connected!");
          }
        } catch (Exception e) {
          logger.warn("Encountered error trying to connect", e);
        } finally {
          if (firstContactSignal != null) {
            // release the signal regardless of event source state or connection request outcome
            firstContactSignal.countDown();
          }
        }

        final Thread execThread = Thread.currentThread();

        while (state.get() == State.OPEN && !execThread.isInterrupted()) {
          if (eventInput == null || eventInput.isClosed()) {
            logger.debug("Connection lost - scheduling reconnect in {} ms", reconnectDelay);
            scheduleReconnect(reconnectDelay);
            break;
          } else {
            this.onEvent(eventInput.read());
          }
        }
      } catch (ServiceUnavailableException ex) {
        logger.debug("Received HTTP 503");
        long delay = reconnectDelay;
        if (ex.hasRetryAfter()) {
          logger.debug("Recovering from HTTP 503 using HTTP Retry-After header value as a reconnect delay");
          final Date requestTime = new Date();
          delay = ex.getRetryTime(requestTime).getTime() - requestTime.getTime();
          delay = (delay > 0) ? delay : 0;
        }

        logger.debug("Recovering from HTTP 503 - scheduling to reconnect in {} ms", delay);
        scheduleReconnect(delay);
      } catch (Exception ex) {
        logger.debug("Recovering from exception -- scheduling reconnect in {} ms", reconnectDelay, ex);
        scheduleReconnect(reconnectDelay);

      } finally {
        if (eventInput != null && !eventInput.isClosed()) {
          eventInput.close();
        }
        logger.debug("Listener task finished.");
      }
    }

    /**
     * Called by the event source when an inbound event is received.
     *
     * This listener aggregator method is responsible for invoking {@link EventSource#onEvent(InboundEvent)}
     * method on the owning event source as well as for notifying all registered {@link EventListener event listeners}.
     *
     * @param event incoming {@link InboundEvent inbound event}.
     */
    @Override
    public void onEvent(final InboundEvent event) {
      if (event == null) {
        return;
      }

      logger.debug("New event received.");

      if (event.getId() != null) {
        lastEventId = event.getId();
      }
      if (event.isReconnectDelaySet()) {
        reconnectDelay = event.getReconnectDelay();
      }

      notify(EventSource.this, event);
      notify(unboundListeners, event);

      final String eventName = event.getName();
      if (eventName != null) {
        final List<EventListener> eventListeners = boundListeners.get(eventName);
        if (eventListeners != null) {
          notify(eventListeners, event);
        }
      }
    }

    private void notify(final Collection<EventListener> listeners, final InboundEvent event) {
      for (EventListener listener : listeners) {
        notify(listener, event);
      }
    }

    private void notify(final EventListener listener, final InboundEvent event) {
      try {
        listener.onEvent(event);
      } catch (Exception ex) {
        logger.warn(String.format("Event notification in a listener of %s class failed.",
              listener.getClass().getName()), ex);
      }
    }

    /**
     * Schedule a new event processor task to reconnect after the specified {@code delay} [milliseconds].
     *
     * If the {@code delay} is zero or negative, the new reconnect task will be scheduled immediately.
     * The {@code reconnectDelay} and {@code lastEventId} field values are propagated into the newly
     * scheduled task.
     * <p>
     * The method will silently abort in case the event source is not {@link EventSource#isOpen() open}.
     * </p>
     *
     * @param delay specifies the amount of time [milliseconds] to wait before attempting a reconnect.
     *              If zero or negative, the new reconnect task will be scheduled immediately.
     */
    private void scheduleReconnect(final long delay) {
      final State s = state.get();
      if (s != State.OPEN) {
        logger.debug("Aborting reconnect of event source in {} state", state);
        return;
      }

      // propagate the current reconnectDelay, but schedule based on the delay parameter
      final EventProcessor processor = new EventProcessor(reconnectDelay, null, headers);
      if (delay > 0) {
        executor.schedule(processor, delay, TimeUnit.MILLISECONDS);
      } else {
        executor.submit(processor);
      }
    }

    private Invocation.Builder prepareHandshakeRequest() {
      final Invocation.Builder request = target.request(SseFeature.SERVER_SENT_EVENTS_TYPE);
      // TODO add the SERVER_SENT_EVENTS_TYPE header
      request.headers(this.headers);
      if (lastEventId != null && !lastEventId.isEmpty()) {
        request.header(SseFeature.LAST_EVENT_ID_HEADER, lastEventId);
      }
      if (disableKeepAlive) {
        request.header("Connection", "close");
      }
      return request;
    }

    /**
     * Await the initial contact with the SSE endpoint.
     */
    public void awaitFirstContact() {
      logger.debug("Awaiting first contact signal.");
      try {
        if (firstContactSignal == null) {
          return;
        }

        try {
          firstContactSignal.await();
        } catch (InterruptedException ex) {
          logger.warn(LocalizationMessages.EVENT_SOURCE_OPEN_CONNECTION_INTERRUPTED(), ex);
          Thread.currentThread().interrupt();
        }
      } finally {
        logger.debug("First contact signal released.");
      }
    }
  }
}