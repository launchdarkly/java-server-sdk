package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventProcessor;
import com.launchdarkly.sdk.server.interfaces.EventSender;
import com.launchdarkly.sdk.server.interfaces.EventSenderFactory;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.net.URI;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class EventProcessorBenchmarks {
  private static final int EVENT_BUFFER_SIZE = 1000;
  private static final int FLAG_COUNT = 10;
  private static final int FLAG_VERSIONS = 3;
  private static final int FLAG_VARIATIONS = 2;
  
  @State(Scope.Thread)
  public static class BenchmarkInputs {
    // Initialization of the things in BenchmarkInputs does not count as part of a benchmark.
    final EventProcessor eventProcessor;
    final MockEventSender eventSender;
    final LDUser basicUser;
    final Random random;

    public BenchmarkInputs() {
      // MockEventSender does no I/O - it discards every event payload. So we are benchmarking
      // all of the event processing steps up to that point, including the formatting of the
      // JSON data in the payload.
      eventSender = new MockEventSender();
      
      eventProcessor = Components.sendEvents()
          .capacity(EVENT_BUFFER_SIZE)
          .eventSender(new MockEventSenderFactory(eventSender))
          .createEventProcessor(TestComponents.clientContext(TestValues.SDK_KEY, LDConfig.DEFAULT));
      
      basicUser = new LDUser("userkey");

      random = new Random();
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
    for (int i = 0; i < 1000; i++) {
      int variation = inputs.randomFlagVariation();
      Event.FeatureRequest event = new Event.FeatureRequest(
          System.currentTimeMillis(),
          inputs.randomFlagKey(),
          inputs.basicUser,
          inputs.randomFlagVersion(),
          variation,
          LDValue.of(variation),
          LDValue.ofNull(),
          null,
          null,
          false, // trackEvents == false: only summary counts are generated
          0L,
          false
          );
      inputs.eventProcessor.sendEvent(event);
    }
    inputs.eventProcessor.flush();
    inputs.eventSender.awaitEvents();
  }

  @Benchmark
  public void featureRequestEventsWithFullTracking(BenchmarkInputs inputs) throws Exception {
    for (int i = 0; i < 1000; i++) {
      int variation = inputs.randomFlagVariation();
      Event.FeatureRequest event = new Event.FeatureRequest(
          System.currentTimeMillis(),
          inputs.randomFlagKey(),
          inputs.basicUser,
          inputs.randomFlagVersion(),
          variation,
          LDValue.of(variation),
          LDValue.ofNull(),
          null,
          null,
          true, // trackEvents == true: the full events are included in the output
          0L,
          false
          );
      inputs.eventProcessor.sendEvent(event);
    }
    inputs.eventProcessor.flush();
    inputs.eventSender.awaitEvents();
  }
  
  @Benchmark
  public void customEvents(BenchmarkInputs inputs) throws Exception {
    LDValue data = LDValue.of("data");
    for (int i = 0; i < 1000; i++) {
      Event.Custom event = new Event.Custom(
          System.currentTimeMillis(),
          "event-key",
          inputs.basicUser,
          data,
          null
          );
      inputs.eventProcessor.sendEvent(event);;
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
    public EventSender createEventSender(String arg0, HttpConfiguration arg1) {
      return instance;
    }
  }
}
