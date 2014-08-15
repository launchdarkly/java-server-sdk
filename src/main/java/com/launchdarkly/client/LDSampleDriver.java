package com.launchdarkly.client;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A simple driver illustrating a basic use of the {@link LDClient} library
 */
public class LDSampleDriver {

  public static void main(String... args) {
    LDClient client = new LDClient("835a5994-d656-4e6e-9175-19780c6ad75f");

    LDUser user = new LDUser.Builder("user@test.com")
        .country("US")
        .custom("groups", Arrays.asList("google", "microsoft"))
        .build();
    boolean flag = client.getFlag("engine.enable", user, false);

    System.out.println("Value of flag is " + flag);
  }
}
