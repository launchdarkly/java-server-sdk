package testapp;

import com.launchdarkly.sdk.*;
import com.launchdarkly.sdk.server.*;
import com.launchdarkly.sdk.server.integrations.*;
import com.google.gson.*;
import org.slf4j.*;

public class TestApp {
  private static final Logger logger = LoggerFactory.getLogger(TestApp.class);

  public static void main(String[] args) throws Exception {
    // Verify that our Redis URI constant is what it should be (test for ch63221)
    if (!RedisDataStoreBuilder.DEFAULT_URI.toString().equals("redis://localhost:6379")) {
      System.out.println("*** error: RedisDataStoreBuilder.DEFAULT_URI is " + RedisDataStoreBuilder.DEFAULT_URI);
      System.exit(1);
    }

    LDConfig config = new LDConfig.Builder()
      .offline(true)
      .build();
    LDClient client = new LDClient("fake-sdk-key", config);

    // The following line is just for the sake of referencing Gson, so we can be sure
    // that it's on the classpath as it should be (i.e. if we're using the "all" jar
    // that provides its own copy of Gson).
    JsonPrimitive x = new JsonPrimitive("x");

    // Also do a flag evaluation, to ensure that it calls NewRelicReflector.annotateTransaction()
    client.boolVariation("flag-key", new LDUser("user-key"), false);

    System.out.println("@@@ successfully created LD client @@@");
  }
}