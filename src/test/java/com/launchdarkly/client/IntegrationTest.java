package com.launchdarkly.client;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;

import static java.util.Collections.singletonList;

@Ignore
public class IntegrationTest {

  @Test
  public void testClient() throws IOException {
    LDUser user = new LDUser.Builder("bob@example.com")
        .firstName("Bob")
        .lastName("Loblaw")
        .customString("groups", singletonList("beta_testers"))
        .build();

    LDConfig config = new LDConfig.Builder()
//        .streamURI(URI.create("https://ld-stg-stream.global.ssl.fastly.net"))
//        .streamURI(URI.create("https://f6bff885.fanoutcdn.com"))

        .stream(false)
        .build();

//    String apiKey = "sdk-707fa2a8-f3be-4f14-a122-946ab580a648";

    //Dan's staging test key:
//    String apiKey = "sdk-6d82ac76-97ce-4877-a661-a5709ca18a63";
//    Dan's prod key:
    String apiKey = "sdk-707fa2a8-f3be-4f14-a122-946ab580a648";
    LDClient client = new LDClient(apiKey, config, 30000L);
    boolean showFeature = client.toggle("YOUR_FEATURE_KEY", user, false);

    if (showFeature) {
      System.out.println("Showing your feature");
    } else {
      System.out.println("Not showing your feature");
    }

    client.flush();
    while(true) {}
  }
}
