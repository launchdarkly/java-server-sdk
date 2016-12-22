package com.launchdarkly.client;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.*;

public class FeatureRequestEventTest {

  @Test
  public void testSerializeNulls() {
    FeatureRequestEvent expectedFre = new FeatureRequestEvent(null, null, null, null, 0, null);
    Gson gson = new Gson();
    String json = gson.toJson(expectedFre);
    FeatureRequestEvent actualFre = gson.fromJson(json, FeatureRequestEvent.class);

    assertNull(actualFre.key);
    assertNull(actualFre.defaultVal);
    assertNull(actualFre.prereqOf);
    assertNull(actualFre.value);
    assertEquals((Integer) 0, actualFre.version);
  }
}
