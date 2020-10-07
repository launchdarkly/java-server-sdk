package testapp;

import com.launchdarkly.sdk.*;
import com.launchdarkly.sdk.json.*;
import com.launchdarkly.sdk.server.*;
import java.util.*;
import org.slf4j.*;

public class TestApp {
  private static final Logger logger = LoggerFactory.getLogger(TestApp.class); // proves SLF4J API is on classpath

  private static List<String> errors = new ArrayList<>();

  public static void main(String[] args) throws Exception {
    try {
      LDConfig config = new LDConfig.Builder()
        .offline(true)
        .build();
      LDClient client = new LDClient("fake-sdk-key", config);
      log("client creation OK");
    } catch (RuntimeException e) {
      addError("client creation failed", e);
    }

    try {
      boolean jsonOk = true;
      for (JsonSerializationTestData.TestItem item: JsonSerializationTestData.TEST_ITEMS) {
        if (!(item instanceof JsonSerializable)) {
          continue; // things without our marker interface, like a Map, can't be passed to JsonSerialization.serialize
        }
        String actualJson = JsonSerialization.serialize((JsonSerializable)item.objectToSerialize);
        if (!JsonSerializationTestData.assertJsonEquals(item.expectedJson, actualJson, item.objectToSerialize)) {
          jsonOk = false;
        }
      }
      if (jsonOk) {
        log("JsonSerialization tests OK");      
      }
    } catch (RuntimeException e) {
      addError("unexpected error in JsonSerialization tests", e);
    }

    try {
      Class.forName("testapp.TestAppGsonTests"); // see TestAppGsonTests for why we're loading it in this way
    } catch (NoClassDefFoundError e) {
      log("skipping LDGson tests because Gson is not in the classpath");
    } catch (RuntimeException e) {
      addError("unexpected error in LDGson tests", e);
    }

    try {
      Class.forName("testapp.TestAppJacksonTests"); // see TestAppJacksonTests for why we're loading it in this way
    } catch (NoClassDefFoundError e) {
      log("skipping LDJackson tests because Jackson is not in the classpath");
    } catch (RuntimeException e) {
      addError("unexpected error in LDJackson tests", e);
    }

    if (errors.isEmpty()) {
      log("PASS");
    } else {
      for (String err: errors) {
        log("ERROR: " + err);
      }
      log("FAIL");
      System.exit(1);
    }
  }

  public static void addError(String message, Throwable e) {
    if (e != null) {
      errors.add(message + ": " + e);
      e.printStackTrace();
    } else {
      errors.add(message);
    }
  }

  public static void log(String message) {
    System.out.println("TestApp: " + message);
  }
}