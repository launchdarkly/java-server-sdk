package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.interfaces.ApplicationInfo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class ApplicationInfoBuilderTest {
  @Test
  public void infoBuilder() {
    ApplicationInfo i1 = Components.applicationInfo()
      .createApplicationInfo();
    assertNull(i1.getApplicationId());
    assertNull(i1.getApplicationVersion());

    ApplicationInfo i2 = Components.applicationInfo()
      .applicationId("authentication-service")
      .applicationVersion("1.0.0")
      .createApplicationInfo();
    assertEquals("authentication-service", i2.getApplicationId());
    assertEquals("1.0.0", i2.getApplicationVersion());
  }
}
