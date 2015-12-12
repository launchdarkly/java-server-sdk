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

  private FeatureRequestor requestor = createMock(FeatureRequestor.class);

  LDClient client = new LDClient("API_KEY") {

    @Override
    protected FeatureRequestor createFeatureRequestor(String apiKey, LDConfig config) {
      return requestor;
    }
  };

  @Test
  public void testExceptionThrownByHttpClientReturnsDefaultValue() throws IOException {

    expect(requestor.makeRequest(anyString(), anyBoolean())).andThrow(new IOException());
    replay(requestor);

    boolean result = client.toggle("test", new LDUser("test.key"), true);
    assertEquals(true, result);
    verify(requestor);
  }
}
