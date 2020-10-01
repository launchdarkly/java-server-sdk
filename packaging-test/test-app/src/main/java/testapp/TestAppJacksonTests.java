package testapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.launchdarkly.sdk.*;
import com.launchdarkly.sdk.json.*;

// This code is in its own class that is loaded dynamically because some of our test scenarios
// involve running TestApp without having Jackson in the classpath, to make sure the SDK does not
// *require* the presence of an external Jackson even though it can interoperate with one.

public class TestAppJacksonTests {
  // Use static block so simply loading this class causes the tests to execute
  static {
    // First try referencing Jackson, so we fail right away if it's not on the classpath
    Class<?> c = ObjectMapper.class;
    try {
      runJacksonTests();
    } catch (Exception e) {
      // If we've even gotten to this static block, then Jackson itself *is* on the application's
      // classpath, so this must be some other kind of classloading error that we do want to
      // report. For instance, a NoClassDefFound error for Jackson at this point, if we're in
      // OSGi, would mean that the SDK bundle is unable to see the external Jackson classes.
      TestApp.addError("unexpected error in LDJackson tests", e);
    }
  }

  public static void runJacksonTests() throws Exception {
    ObjectMapper jacksonMapper = new ObjectMapper();
    jacksonMapper.registerModule(LDJackson.module());

    boolean ok = true;
    for (JsonSerializationTestData.TestItem item: JsonSerializationTestData.TEST_ITEMS) {
      String actualJson = jacksonMapper.writeValueAsString(item.objectToSerialize);
      if (!JsonSerializationTestData.assertJsonEquals(item.expectedJson, actualJson, item.objectToSerialize)) {
        ok = false;
      }
    }

    if (ok) {
      TestApp.log("LDJackson tests OK");
    }
  }
}