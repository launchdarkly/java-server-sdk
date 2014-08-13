package com.launchdarkly.client;

/**
 * A simple driver illustrating a basic use of the {@link LDClient} library
 */
public class LDSampleDriver {

  public static void main(String... args) {
    LDClient client = new LDClient("835a5994-d656-4e6e-9175-19780c6ad75f");

    LDUser user = new LDUser("user@test.com");
    boolean flag = client.getFlag("engine.enable", user, false);

    System.out.println("Value of flag is " + flag);
  }
}
