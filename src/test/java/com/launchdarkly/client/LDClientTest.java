package com.launchdarkly.client;

import java.net.URI;
import java.util.Arrays;
import static org.junit.Assert.*;
import org.junit.*;

public class LDClientTest {
  LDClient client;

  // TODO create / delete the feature here via API

  @Before
  public void setupLDClient() {
    LDConfig config = new LDConfig("7f60f21f-0552-4756-ae32-ca65a0c96ca8", URI.create("http://localhost:8080"));
    client = new LDClient(config);
  }

  @Test
  public void getFlagReturnsTrueForEnabledUser() {
    LDUser user = new LDUser.Builder("user@test.com")
        .country("USA")
        .custom("groups", Arrays.asList("google", "microsoft"))
        .build();
    boolean flag = client.getFlag("engine.enable", user, false);
    assertEquals(flag, true);
  }
}
