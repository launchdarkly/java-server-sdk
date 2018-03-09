package com.launchdarkly.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static org.hamcrest.Matchers.equalTo;

public class TestUtils {

  public static Matcher<JsonElement> hasJsonProperty(final String name, JsonElement value) {
    return hasJsonProperty(name, equalTo(value));
  }

  public static Matcher<JsonElement> hasJsonProperty(final String name, String value) {
    return hasJsonProperty(name, new JsonPrimitive(value));
  }
    
  public static Matcher<JsonElement> hasJsonProperty(final String name, int value) {
    return hasJsonProperty(name, new JsonPrimitive(value));
  }
    
  public static Matcher<JsonElement> hasJsonProperty(final String name, double value) {
    return hasJsonProperty(name, new JsonPrimitive(value));
  }
    
  public static Matcher<JsonElement> hasJsonProperty(final String name, boolean value) {
    return hasJsonProperty(name, new JsonPrimitive(value));
  }
    
  public static Matcher<JsonElement> hasJsonProperty(final String name, final Matcher<JsonElement> matcher) {
    return new TypeSafeDiagnosingMatcher<JsonElement>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(name + ": ");
        matcher.describeTo(description);
      }
      
      @Override
      protected boolean matchesSafely(JsonElement item, Description mismatchDescription) {
        JsonElement value = item.getAsJsonObject().get(name);
        if (!matcher.matches(value)) {
          matcher.describeMismatch(value, mismatchDescription);
          return false;
        }
        return true;
      }
    };
  }

  public static Matcher<JsonElement> isJsonArray(final Matcher<Iterable<? super JsonElement>> matcher) {
    return new TypeSafeDiagnosingMatcher<JsonElement>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("array: ");
        matcher.describeTo(description);
      }
      
      @Override
      protected boolean matchesSafely(JsonElement item, Description mismatchDescription) {
        JsonArray value = item.getAsJsonArray();
        if (!matcher.matches(value)) {
          matcher.describeMismatch(value, mismatchDescription);
          return false;
        }
        return true;
      }
    };
  }
}
