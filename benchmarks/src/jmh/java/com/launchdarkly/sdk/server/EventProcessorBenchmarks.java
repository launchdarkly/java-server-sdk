package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.EventSender;
import com.launchdarkly.sdk.server.subsystems.EventSenderFactory;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static com.launchdarkly.sdk.server.TestValues.BASIC_CONTEXT;
import static com.launchdarkly.sdk.server.TestValues.CUSTOM_EVENT;
import static com.launchdarkly.sdk.server.TestValues.TEST_EVENTS_COUNT;

public class EventProcessorBenchmarks {
  private static final int EVENT_BUFFER_SIZE = 1000;
  private static final int FLAG_COUNT = 10;
  private static final int FLAG_VERSIONS = 3;
  private static final int FLAG_VARIATIONS = 2;
  
  @State(Scope.Thread)
  public static class BenchmarkInputs {
    // Initialization of the things in BenchmarkInputs does not count as part of a benchmark.
    final DefaultEventProcessor eventProcessor;
    final MockEventSender eventSender;
    final List<Event.FeatureRequest> featureRequestEventsWithoutTracking = new ArrayList<>();
    final List<Event.FeatureRequest> featureRequestEventsWithTracking = new ArrayList<>();
    final Random random;

    public BenchmarkInputs() {
      // MockEventSender does no I/O - it discards every event payload. So we are benchmarking
      // all of the event processing steps up to that point, including the formatting of the
      // JSON data in the payload.
      eventSender = new MockEventSender();
      
      EventsConfiguration eventsConfig = new EventsConfiguration(
          false,
          EVENT_BUFFER_SIZE,
          new ServerSideEventContextDeduplicator(1000, Duration.ofHours(1)),
          null,
          null,
          eventSender,
          null,
          Duration.ofHours(1),
          null
          );
      eventProcessor = new DefaultEventProcessor(eventsConfig, TestComponents.sharedExecutor, Thread.MAX_PRIORITY,
          LDLogger.none());
      
      random = new Random();
      
      for (int i = 0; i < TEST_EVENTS_COUNT; i++) {
        String flagKey = "flag" + random.nextInt(FLAG_COUNT);
        int version = random.nextInt(FLAG_VERSIONS) + 1;
        int variation = random.nextInt(FLAG_VARIATIONS);
        for (boolean trackEvents: new boolean[] { false, true }) {
          Event.FeatureRequest event = new Event.FeatureRequest(
              System.currentTimeMillis(),
              flagKey,
              BASIC_CONTEXT,
              version,
              variation,
              LDValue.of(variation),
              LDValue.ofNull(),
              null,
              null,
              trackEvents,
              null,
              false
              );
          (trackEvents ? featureRequestEventsWithTracking : featureRequestEventsWithoutTracking).add(event);
        }
      }
    }
    
    public String randomFlagKey() {
      return "flag" + random.nextInt(FLAG_COUNT);
    }
    
    public int randomFlagVersion() {
      return random.nextInt(FLAG_VERSIONS) + 1;
    }
    
    public int randomFlagVariation() {
      return random.nextInt(FLAG_VARIATIONS);
    }
  }
  
  @Benchmark
  public void summarizeFeatureRequestEvents(BenchmarkInputs inputs) throws Exception {
    for (Event.FeatureRequest event: inputs.featureRequestEventsWithoutTracking) {
      inputs.eventProcessor.sendEvent(event);
    }
    inputs.eventProcessor.flush();
    inputs.eventSender.awaitEvents();
  }

  @Benchmark
  public void featureRequestEventsWithFullTracking(BenchmarkInputs inputs) throws Exception {
    for (Event.FeatureRequest event: inputs.featureRequestEventsWithTracking) {
      inputs.eventProcessor.sendEvent(event);
    }
    inputs.eventProcessor.flush();
    inputs.eventSender.awaitEvents();
  }
  
  @Benchmark
  public void customEvents(BenchmarkInputs inputs) throws Exception {
    for (int i = 0; i < TEST_EVENTS_COUNT; i++) {
      inputs.eventProcessor.sendEvent(CUSTOM_EVENT);
    }
    inputs.eventProcessor.flush();
    inputs.eventSender.awaitEvents();
  }
  
  private static final class MockEventSender implements EventSender {
    private static final Result RESULT = new Result(true, false, null);
    private static final CountDownLatch counter = new CountDownLatch(1);
    
    @Override
    public void close() throws IOException {}

    @Override
    public Result sendEventData(EventDataKind arg0, String arg1, int arg2, URI arg3) {
      counter.countDown();
      return RESULT;
    }
    
    public void awaitEvents() throws InterruptedException {
      counter.await();
    }
  }
  
  private static final class MockEventSenderFactory implements EventSenderFactory {
    private final MockEventSender instance;
    
    MockEventSenderFactory(MockEventSender instance) {
      this.instance = instance;
    }
    
    @Override
    public EventSender createEventSender(ClientContext clientContext) {
      return instance;
    }
  }
}
