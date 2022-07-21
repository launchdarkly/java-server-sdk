package testapp;

import com.launchdarkly.sdk.*;
import java.util.*;

public class JsonSerializationTestData {
  public static class TestItem {
    final Object objectToSerialize;
    final String expectedJson;

    private TestItem(Object objectToSerialize, String expectedJson) {
      this.objectToSerialize = objectToSerialize;
      this.expectedJson = expectedJson;
    }
  }

  public static TestItem[] TEST_ITEMS = new TestItem[] {
    new TestItem(
      LDValue.buildArray().add(1).add(2).build(),
      "[1,2]"
    ),
    new TestItem(
      Collections.singletonMap("value", LDValue.buildArray().add(1).add(2).build()),
      "{\"value\":[1,2]}"
    ),
    new TestItem(
      EvaluationReason.off(),
      "{\"kind\":\"OFF\"}"
    ),
    new TestItem(
      LDContext.create("userkey"),
      "{\"kind\":\"user\",\"key\":\"userkey\"}"
    )
  };

  public static boolean assertJsonEquals(String expectedJson, String actualJson, Object objectToSerialize) {
    if (!LDValue.parse(actualJson).equals(LDValue.parse(expectedJson))) {
      TestApp.addError("JSON encoding of " + objectToSerialize.getClass() + " should have been " +
        expectedJson + ", was " + actualJson, null);
      return false;
    }
    return true;
  }
}