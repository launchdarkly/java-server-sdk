package com.launchdarkly.client;

/**
 * A simple driver illustrating a basic use of the {@link com.launchdarkly.client.LaunchDarklyClient} library
 */
class Driver {

  public static void main(String... args) {
    LaunchDarklyClient client = new LaunchDarklyClient("835a5994-d656-4e6e-9175-19780c6ad75f");

    User user = new User("user@test.com");
    boolean flag = client.getFlag("engine.enable", user, false);

    System.out.println("Value of flag is " + flag);
  }
}
