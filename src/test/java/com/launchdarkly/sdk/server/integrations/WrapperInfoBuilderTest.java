package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.interfaces.ApplicationInfo;

import com.launchdarkly.sdk.server.interfaces.WrapperInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class WrapperInfoBuilderTest {
  @Test
  public void theDefaultInstanceContainsNullValues() {
    WrapperInfo defaultInstance = Components.wrapperInfo()
        .build();
    assertNull(defaultInstance.getWrapperName());
    assertNull(defaultInstance.getWrapperVersion());
  }

  @Test
  public void setValuesAreReflectedInBuiltInstance() {
    WrapperInfo clojureWrapper = Components.wrapperInfo()
      .wrapperName("Clojure")
      .wrapperVersion("0.0.1")
      .build();
    assertEquals("Clojure", clojureWrapper.getWrapperName());
    assertEquals("0.0.1", clojureWrapper.getWrapperVersion());
  }
}
