package com.launchdarkly.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;

public class TestUtil {

  public static FeatureStoreFactory specificFeatureStore(final FeatureStore store) {
    return new FeatureStoreFactory() {
      public FeatureStore createFeatureStore() {
        return store;
      }
    };
  }
  
  public static FeatureStore initedFeatureStore() {
    FeatureStore store = new InMemoryFeatureStore();
    store.init(Collections.<VersionedDataKind<?>, Map<String, ? extends VersionedData>>emptyMap());
    return store;
  }
  
  public static EventProcessorFactory specificEventProcessor(final EventProcessor ep) {
    return new EventProcessorFactory() {
      public EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
        return ep;
      }
    };
  }
  
  public static UpdateProcessorFactory specificUpdateProcessor(final UpdateProcessor up) {
    return new UpdateProcessorFactory() {
      public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
        return up;
      }
    };
  }
  
  public static class TestEventProcessor implements EventProcessor {
    List<Event> events = new ArrayList<>();

    @Override
    public void close() throws IOException {}

    @Override
    public void sendEvent(Event e) {
      events.add(e);
    }

    @Override
    public void flush() {}
  }
  
  public static JsonPrimitive js(String s) {
    return new JsonPrimitive(s);
  }

  public static JsonPrimitive jint(int n) {
    return new JsonPrimitive(n);
  }

  public static JsonPrimitive jdouble(double d) {
    return new JsonPrimitive(d);
  }
  
  public static JsonPrimitive jbool(boolean b) {
    return new JsonPrimitive(b);
  }
  
  public static VariationOrRollout fallthroughVariation(int variation) {
    return new VariationOrRollout(variation, null);
  }
  
  public static FeatureFlag booleanFlagWithClauses(String key, Clause... clauses) {
    Rule rule = new Rule(null, Arrays.asList(clauses), 1, null);
    return new FeatureFlagBuilder(key)
        .on(true)
        .rules(Arrays.asList(rule))
        .fallthrough(fallthroughVariation(0))
        .offVariation(0)
        .variations(jbool(false), jbool(true))
        .build();
  }

  public static FeatureFlag flagWithValue(String key, JsonElement value) {
    return new FeatureFlagBuilder(key)
        .on(false)
        .offVariation(0)
        .variations(value)
        .build();
  }
  
  public static EvaluationDetails<JsonElement> simpleEvaluation(int variation, JsonElement value) {
    return new EvaluationDetails<>(EvaluationReason.fallthrough(), 0, value);
  }
  
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
