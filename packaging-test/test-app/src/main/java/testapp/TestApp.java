package testapp;

import com.launchdarkly.sdk.*;
import com.launchdarkly.sdk.server.*;
import org.slf4j.*;

public class TestApp {
  private static final Logger logger = LoggerFactory.getLogger(TestApp.class);

  public static void main(String[] args) throws Exception {
    LDConfig config = new LDConfig.Builder()
      .offline(true)
      .build();
    LDClient client = new LDClient("fake-sdk-key", config);

    // Also do a flag evaluation, to ensure that it calls NewRelicReflector.annotateTransaction()
    client.boolVariation("flag-key", new LDUser("user-key"), false);

    System.out.println("@@@ successfully created LD client @@@");
  }
}