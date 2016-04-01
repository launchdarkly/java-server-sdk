package com.launchdarkly.client;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

import org.apache.http.impl.client.CloseableHttpClient;
import org.easymock.*;
import org.junit.Test;

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
