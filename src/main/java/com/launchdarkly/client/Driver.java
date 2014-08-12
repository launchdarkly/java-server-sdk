package com.launchdarkly.client;

import java.net.URI;

public class Driver {

  public static void main(String... args) {
    Config config = new Config("7f60f21f-0552-4756-ae32-ca65a0c96ca8", URI.create("http://localhost:3000"));
    LaunchDarklyClient client = new LaunchDarklyClient(config);

    User user = new User("user@test.com");
    boolean flag = client.getFlag("engine.enable", user, false);

    System.out.println("Value of flag is " + flag);
  }
}
