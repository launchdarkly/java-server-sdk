package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.subsystems.HookConfiguration;
import org.junit.Test;

import java.util.Arrays;

import static org.easymock.EasyMock.mock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class HookConfigurationBuilderTest {

  @Test
  public void emptyHooksAsDefault() {
    HookConfiguration configuration = Components.hooks().build();
    assertEquals(0, configuration.getHooks().size());
  }

  @Test
  public void canSetHooks() {
    Hook hookA = mock(Hook.class);
    Hook hookB = mock(Hook.class);
    HookConfiguration configuration = Components.hooks().setHooks(Arrays.asList(hookA, hookB)).build();
    assertEquals(2, configuration.getHooks().size());
    assertSame(hookA, configuration.getHooks().get(0));
    assertSame(hookB, configuration.getHooks().get(1));
  }
}
