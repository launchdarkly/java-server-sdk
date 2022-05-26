package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.interfaces.ServiceEndpoints;

import java.net.URI;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class ServiceEndpointsBuilderTest {
  @Test
  public void usesAllDefaultUrisIfNoneAreOverridden() {
    ServiceEndpoints se = Components.serviceEndpoints().createServiceEndpoints();
    assertEquals(URI.create("https://stream.launchdarkly.com"), se.getStreamingBaseUri());
    assertEquals(URI.create("https://app.launchdarkly.com"), se.getPollingBaseUri());
    assertEquals(URI.create("https://events.launchdarkly.com"), se.getEventsBaseUri());
  }

  @Test
  public void canSetAllUrisToCustomValues() {
    URI su = URI.create("https://my-streaming");
    URI pu = URI.create("https://my-polling");
    URI eu = URI.create("https://my-events");
    ServiceEndpoints se = Components.serviceEndpoints()
      .streaming(su)
      .polling(pu)
      .events(eu)
      .createServiceEndpoints();
    assertEquals(su, se.getStreamingBaseUri());
    assertEquals(pu, se.getPollingBaseUri());
    assertEquals(eu, se.getEventsBaseUri());
  }

  @Test
  public void ifCustomUrisAreSetAnyUnsetOnesDefaultToNull() {
    URI su = URI.create("https://my-streaming");
    URI pu = URI.create("https://my-polling");
    URI eu = URI.create("https://my-events");
    ServiceEndpoints se1 = Components.serviceEndpoints().streaming(su).createServiceEndpoints();
    assertEquals(su, se1.getStreamingBaseUri());
    assertNull(se1.getPollingBaseUri());
    assertNull(se1.getEventsBaseUri());

    ServiceEndpoints se2 = Components.serviceEndpoints().polling(pu).createServiceEndpoints();
    assertNull(se2.getStreamingBaseUri());
    assertEquals(pu, se2.getPollingBaseUri());
    assertNull(se2.getEventsBaseUri());

    ServiceEndpoints se3 = Components.serviceEndpoints().events(eu).createServiceEndpoints();
    assertNull(se3.getStreamingBaseUri());
    assertNull(se3.getPollingBaseUri());
    assertEquals(eu, se3.getEventsBaseUri());
  }

  @Test
  public void settingRelayProxyUriSetsAllUris() {
    URI customRelay = URI.create("http://my-relay");
    ServiceEndpoints se = Components.serviceEndpoints().relayProxy(customRelay).createServiceEndpoints();
    assertEquals(customRelay, se.getStreamingBaseUri());
    assertEquals(customRelay, se.getPollingBaseUri());
    assertEquals(customRelay, se.getEventsBaseUri());
  }

  @Test
  public void stringSettersAreEquivalentToUriSetters() {
    String su = "https://my-streaming";
    String pu = "https://my-polling";
    String eu = "https://my-events";
    ServiceEndpoints se1 = Components.serviceEndpoints().streaming(su).polling(pu).events(eu).createServiceEndpoints();
    assertEquals(URI.create(su), se1.getStreamingBaseUri());
    assertEquals(URI.create(pu), se1.getPollingBaseUri());
    assertEquals(URI.create(eu), se1.getEventsBaseUri());

    String ru = "http://my-relay";
    ServiceEndpoints se2 = Components.serviceEndpoints().relayProxy(ru).createServiceEndpoints();
    assertEquals(URI.create(ru), se2.getStreamingBaseUri());
    assertEquals(URI.create(ru), se2.getPollingBaseUri());
    assertEquals(URI.create(ru), se2.getEventsBaseUri());
  }
}
