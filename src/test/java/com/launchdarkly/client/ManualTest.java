package com.launchdarkly.client;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

@Ignore
public class ManualTest {
  private static final Logger logger = LoggerFactory.getLogger(ManualTest.class);

  @Test
  public void manualTest() throws URISyntaxException {
    LDConfig config = new LDConfig.Builder()
        .startWaitMillis(30000L)
        .baseURI(URI.create("https://ld-stg.global.ssl.fastly.net"))
//        .streamURI(URI.create("https://f6bff885.fanoutcdn.com"))
//        .eventsURI(URI.create("https://events-stg.launchdarkly.com"))
        .stream(false)
        .build();

    //my prod key:
//    LDClient ldClient = new LDClient("sdk-fdd9a27d-7939-41a1-bf36-b64798d93372", config);

    //staging
    LDClient ldClient = new LDClient("sdk-0b5766c3-50fa-427e-be3b-50fd3c631c5d", config);

    LDUser user = new LDUser.Builder("user1Key").build();
    System.out.println(ldClient.toggle("abc", user, false));


//    while (true) {
//    }
  }
}
