package testapp;

import com.google.gson.*;
import com.launchdarkly.sdk.*;
import com.launchdarkly.sdk.json.*;

// This code is in its own class that is loaded dynamically because some of our test scenarios
// involve running TestApp without having Gson in the classpath, to make sure the SDK does not
// *require* the presence of an external Gson even though it can interoperate with one.

public class TestAppGsonTests {
  // Use static block so simply loading this class causes the tests to execute
  static {
    // First try referencing Gson, so we fail right away if it's not on the classpath
    Class<?> c = Gson.class;
    try {
      runGsonTests();
    } catch (NoClassDefFoundError e) {
      // If we've even gotten to this static block, then Gson itself *is* on the application's
      // classpath, so this must be some other kind of classloading error that we do want to
      // report. For instance, a NoClassDefFound error for Gson at this point, if we're in
      // OSGi, would mean that the SDK bundle is unable to see the external Gson classes.
      TestApp.addError("unexpected error in LDGson tests", e);
    }
  }

  public static void runGsonTests() {
    Gson gson = new GsonBuilder().registerTypeAdapterFactory(LDGson.typeAdapters()).create();

    boolean ok = true;
    for (JsonSerializationTestData.TestItem item: JsonSerializationTestData.TEST_ITEMS) {
      String actualJson = gson.toJson(item.objectToSerialize);
      if (!JsonSerializationTestData.assertJsonEquals(item.expectedJson, actualJson, item.objectToSerialize)) {
        ok = false;
      }
    }

    if (ok) {
      TestApp.log("LDGson tests OK");
    }
  }
}