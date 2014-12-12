package com.launchdarkly.client;

import static org.easymock.EasyMock.*;
import static org.easymock.EasyMock.createMock;
import static org.junit.Assert.assertEquals;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.easymock.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.io.IOException;

public class LDClientTest extends EasyMockSupport {

  private CloseableHttpClient httpClient = createMock(CloseableHttpClient.class);

  private EventProcessor eventProcessor = createMock(EventProcessor.class);

  LDClient client = new LDClient("API_KEY") {
    @Override
    protected CloseableHttpClient createClient() {
      return httpClient;
    }

    @Override
    protected EventProcessor createEventProcessor(String apiKey, LDConfig config) {
      return eventProcessor;
    }
  };

  @Test
  public void testExceptionThrownByHttpClientReturnsDefaultValue() throws IOException {

    expect(httpClient.execute(anyObject(HttpUriRequest.class), anyObject(HttpContext.class))).andThrow(new RuntimeException());
    replay(httpClient);

    boolean result = client.getFlag("test", new LDUser("test.key"), true);
    assertEquals(true, result);
    verify(httpClient);
  }
}
