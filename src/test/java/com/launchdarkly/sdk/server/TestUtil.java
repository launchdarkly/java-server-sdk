package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeListener;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@SuppressWarnings("javadoc")
public class TestUtil {
  /**
   * We should use this instead of JsonHelpers.gsonInstance() in any test code that might be run from
   * outside of this project (for instance, from java-server-sdk-redis or other integrations), because
   * in that context the SDK classes might be coming from the default jar distribution where Gson is
   * shaded. Therefore, if a test method tries to call an SDK implementation method like gsonInstance()
   * that returns a Gson type, or one that takes an argument of a Gson type, that might fail at runtime
   * because the Gson type has been changed to a shaded version.
   */
  public static final Gson TEST_GSON_INSTANCE = new Gson();

  public static void upsertFlag(DataStore store, FeatureFlag flag) {
    store.upsert(FEATURES, flag.getKey(), new ItemDescriptor(flag.getVersion(), flag));
  }
  
  public static void upsertSegment(DataStore store, Segment segment) {
    store.upsert(SEGMENTS, segment.getKey(), new ItemDescriptor(segment.getVersion(), segment));
  }
  
  public static class FlagChangeEventSink extends FlagChangeEventSinkBase<FlagChangeEvent> implements FlagChangeListener {
    @Override
    public void onFlagChange(FlagChangeEvent event) {
      events.add(event);
    }
  }

  public static class FlagValueChangeEventSink extends FlagChangeEventSinkBase<FlagValueChangeEvent> implements FlagValueChangeListener {
    @Override
    public void onFlagValueChange(FlagValueChangeEvent event) {
      events.add(event);
    }
  }

  private static class FlagChangeEventSinkBase<T extends FlagChangeEvent> {
    protected final BlockingQueue<T> events = new ArrayBlockingQueue<>(100);

    public T awaitEvent() {
      try {
        T event = events.poll(1, TimeUnit.SECONDS);
        assertNotNull("expected flag change event", event);
        return event;
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    
    public void expectEvents(String... flagKeys) {
      Set<String> expectedChangedFlagKeys = ImmutableSet.copyOf(flagKeys);
      Set<String> actualChangedFlagKeys = new HashSet<>();
      for (int i = 0; i < expectedChangedFlagKeys.size(); i++) {
        try {
          T e = events.poll(1, TimeUnit.SECONDS);
          if (e == null) {
            fail("expected change events for " + expectedChangedFlagKeys + " but got " + actualChangedFlagKeys);
          }
          actualChangedFlagKeys.add(e.getKey());
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      assertThat(actualChangedFlagKeys, equalTo(expectedChangedFlagKeys));
      expectNoEvents();
    }
    
    public void expectNoEvents() {
      try {
        T event = events.poll(100, TimeUnit.MILLISECONDS);
        assertNull("expected no more flag change events", event);
      } catch (InterruptedException e) {}
    }
  }
  
  public static Evaluator.EvalResult simpleEvaluation(int variation, LDValue value) {
    return new Evaluator.EvalResult(value, variation, EvaluationReason.fallthrough());
  }
  
  public static Matcher<LDValue> hasJsonProperty(final String name, LDValue value) {
    return hasJsonProperty(name, equalTo(value));
  }

  public static Matcher<LDValue> hasJsonProperty(final String name, String value) {
    return hasJsonProperty(name, LDValue.of(value));
  }
    
  public static Matcher<LDValue> hasJsonProperty(final String name, int value) {
    return hasJsonProperty(name, LDValue.of(value));
  }
    
  public static Matcher<LDValue> hasJsonProperty(final String name, double value) {
    return hasJsonProperty(name, LDValue.of(value));
  }
    
  public static Matcher<LDValue> hasJsonProperty(final String name, boolean value) {
    return hasJsonProperty(name, LDValue.of(value));
  }
    
  public static Matcher<LDValue> hasJsonProperty(final String name, final Matcher<LDValue> matcher) {
    return new TypeSafeDiagnosingMatcher<LDValue>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(name + ": ");
        matcher.describeTo(description);
      }
      
      @Override
      protected boolean matchesSafely(LDValue item, Description mismatchDescription) {
        LDValue value = item.get(name);
        if (!matcher.matches(value)) {
          matcher.describeMismatch(value, mismatchDescription);
          return false;
        }
        return true;
      }
    };
  }

  public static Matcher<LDValue> isJsonArray(final Matcher<Iterable<? extends LDValue>> matcher) {
    return new TypeSafeDiagnosingMatcher<LDValue>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("array: ");
        matcher.describeTo(description);
      }
      
      @Override
      protected boolean matchesSafely(LDValue item, Description mismatchDescription) {
        if (item.getType() != LDValueType.ARRAY) {
          matcher.describeMismatch(item, mismatchDescription);
          return false;
        } else {
          Iterable<LDValue> values = item.values();
          if (!matcher.matches(values)) {
            matcher.describeMismatch(values, mismatchDescription);
            return false;
          }
        }
        return true;
      }
    };
  }
}
