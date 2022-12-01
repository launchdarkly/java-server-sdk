package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.internal.events.EventContextDeduplicator;

import org.junit.Test;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("javadoc")
public class ServerSideEventContextDeduplicatorTest {
  private static final Duration LONG_INTERVAL = Duration.ofHours(3);
  
  @Test
  public void configuredFlushIntervalIsReturned() {
    EventContextDeduplicator ecd = new ServerSideEventContextDeduplicator(1000, LONG_INTERVAL);
    assertThat(ecd.getFlushInterval(), equalTo(LONG_INTERVAL.toMillis()));
  }
  
  @Test
  public void singleKindContextKeysAreDeduplicated() {
    EventContextDeduplicator ecd = new ServerSideEventContextDeduplicator(1000, LONG_INTERVAL);
    
    assertThat(ecd.processContext(LDContext.create("a")), is(true));
    assertThat(ecd.processContext(LDContext.create("b")), is(true));
    assertThat(ecd.processContext(LDContext.create("a")), is(false));
    assertThat(ecd.processContext(LDContext.create("c")), is(true));
    assertThat(ecd.processContext(LDContext.create("c")), is(false));
    assertThat(ecd.processContext(LDContext.create("b")), is(false));
  }
  
  @Test
  public void keysAreDisambiguatedByKind() {
    EventContextDeduplicator ecd = new ServerSideEventContextDeduplicator(1000, LONG_INTERVAL);
    ContextKind kind1 = ContextKind.of("kind1"), kind2 = ContextKind.of("kind2");
    
    assertThat(ecd.processContext(LDContext.create(kind1, "a")), is(true));
    assertThat(ecd.processContext(LDContext.create(kind1, "b")), is(true));
    assertThat(ecd.processContext(LDContext.create(kind1, "a")), is(false));
    assertThat(ecd.processContext(LDContext.create(kind2, "a")), is(true));
    assertThat(ecd.processContext(LDContext.create(kind2, "a")), is(false));
  }
  
  @Test
  public void multiKindContextIsDisambiguatedFromSingleKinds() {
    // This should work automatically because of the defined behavior of LDContext.fullyQualifiedKey()
    EventContextDeduplicator ecd = new ServerSideEventContextDeduplicator(1000, LONG_INTERVAL);
    ContextKind kind1 = ContextKind.of("kind1"), kind2 = ContextKind.of("kind2");
    
    LDContext c1 = LDContext.create(kind1, "a");
    LDContext c2 = LDContext.create(kind2, "a");
    LDContext mc = LDContext.createMulti(c1, c2);
    
    assertThat(ecd.processContext(c1), is(true));
    assertThat(ecd.processContext(c2), is(true));
    assertThat(ecd.processContext(c1), is(false));
    assertThat(ecd.processContext(c2), is(false));
    assertThat(ecd.processContext(mc), is(true));
    assertThat(ecd.processContext(mc), is(false));
  }
}
