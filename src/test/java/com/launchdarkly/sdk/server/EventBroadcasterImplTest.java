package com.launchdarkly.sdk.server;

import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("javadoc")
public class EventBroadcasterImplTest extends BaseTest {
  private EventBroadcasterImpl<FakeListener, FakeEvent> broadcaster =
      new EventBroadcasterImpl<>(FakeListener::sendEvent, sharedExecutor, testLogger);
  
  @Test
  public void sendingEventWithNoListenersDoesNotCauseError() {
    broadcaster.broadcast(new FakeEvent());
  }

  @Test
  public void sendingEventWithNoExecutorDoesNotCauseError() {
    new EventBroadcasterImpl<>(FakeListener::sendEvent, null, testLogger).broadcast(new FakeEvent());
  }
  
  @Test
  public void hasListeners() {
    assertThat(broadcaster.hasListeners(), is(false));
    
    FakeListener listener1 = e -> {};
    FakeListener listener2 = e -> {};
    broadcaster.register(listener1);
    broadcaster.register(listener2);
    
    assertThat(broadcaster.hasListeners(), is(true));
    
    broadcaster.unregister(listener1);
    
    assertThat(broadcaster.hasListeners(), is(true));
    
    broadcaster.unregister(listener2);
    
    assertThat(broadcaster.hasListeners(), is(false));
  }
  
  @Test
  public void allListenersReceiveEvent() throws Exception {
    BlockingQueue<FakeEvent> events1 = new LinkedBlockingQueue<>();
    BlockingQueue<FakeEvent> events2 = new LinkedBlockingQueue<>();
    FakeListener listener1 = events1::add;
    FakeListener listener2 = events2::add;
    broadcaster.register(listener1);
    broadcaster.register(listener2);
    
    FakeEvent e1 = new FakeEvent();
    FakeEvent e2 = new FakeEvent();
    
    broadcaster.broadcast(e1);
    broadcaster.broadcast(e2);
    
    assertThat(events1.take(), is(e1));
    assertThat(events1.take(), is(e2));
    assertThat(events1.isEmpty(), is(true));
    
    assertThat(events2.take(), is(e1));
    assertThat(events2.take(), is(e2));
    assertThat(events2.isEmpty(), is(true));
  }
  
  @Test
  public void canUnregisterListener() throws Exception {
    BlockingQueue<FakeEvent> events1 = new LinkedBlockingQueue<>();
    BlockingQueue<FakeEvent> events2 = new LinkedBlockingQueue<>();
    FakeListener listener1 = events1::add;
    FakeListener listener2 = events2::add;
    broadcaster.register(listener1);
    broadcaster.register(listener2);
    
    FakeEvent e1 = new FakeEvent();
    FakeEvent e2 = new FakeEvent();
    FakeEvent e3 = new FakeEvent();
    
    broadcaster.broadcast(e1);
    
    broadcaster.unregister(listener2);
    broadcaster.broadcast(e2);
    
    broadcaster.register(listener2);
    broadcaster.broadcast(e3);
    
    assertThat(events1.take(), is(e1));
    assertThat(events1.take(), is(e2));
    assertThat(events1.take(), is(e3));
    assertThat(events1.isEmpty(), is(true));
    
    assertThat(events2.take(), is(e1));
    assertThat(events2.take(), is(e3)); // did not get e2
    assertThat(events2.isEmpty(), is(true));
  }
  
  @Test
  public void exceptionFromEarlierListenerDoesNotInterfereWithLaterListener() throws Exception {
    FakeListener listener1 = e -> {
      throw new RuntimeException("sorry");
    };
    broadcaster.register(listener1);
    
    BlockingQueue<FakeEvent> events2 = new LinkedBlockingQueue<>();
    FakeListener listener2 = events2::add;
    broadcaster.register(listener2);
    
    FakeEvent e = new FakeEvent();
    broadcaster.broadcast(e);
    
    assertThat(events2.take(), is(e));
  }
  
  static class FakeEvent {}
  
  static interface FakeListener {
    void sendEvent(FakeEvent e);
  }
}
