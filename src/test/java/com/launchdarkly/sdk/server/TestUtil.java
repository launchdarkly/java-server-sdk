package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
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

  public static String getSdkVersion() {
    return Version.SDK_VERSION;
  }
  
  // repeats until action returns non-null value, throws exception on timeout
  public static <T> T repeatWithTimeout(Duration timeout, Duration interval, Supplier<T> action) {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      T result = action.get();
      if (result != null) {
        return result;
      }
      try {
        Thread.sleep(interval.toMillis());
      } catch (InterruptedException e) { // it's annoying to have to keep declaring this exception further up the call chain
        throw new RuntimeException(e);
      }
    }
    throw new RuntimeException("timed out after " + timeout);
  }
  
  public static void upsertFlag(DataStore store, FeatureFlag flag) {
    store.upsert(FEATURES, flag.getKey(), new ItemDescriptor(flag.getVersion(), flag));
  }
  
  public static void upsertSegment(DataStore store, Segment segment) {
    store.upsert(SEGMENTS, segment.getKey(), new ItemDescriptor(segment.getVersion(), segment));
  }

  public static void shouldNotTimeOut(Future<?> future, Duration interval) throws ExecutionException, InterruptedException {
    try {
      future.get(interval.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException ignored) {
      fail("Should not have timed out");
    }
  }
  
  public static void shouldTimeOut(Future<?> future, Duration interval) throws ExecutionException, InterruptedException {
    try {
      future.get(interval.toMillis(), TimeUnit.MILLISECONDS);
      fail("Expected timeout");
    } catch (TimeoutException ignored) {
    }
  }

  public static DataSourceStatusProvider.Status requireDataSourceStatus(BlockingQueue<DataSourceStatusProvider.Status> statuses) {
    try {
      DataSourceStatusProvider.Status status = statuses.poll(1, TimeUnit.SECONDS);
      assertNotNull(status);
      return status;
    } catch (InterruptedException e) { // it's annoying to have to keep declaring this exception further up the call chain
      throw new RuntimeException(e);
    }
  }
  
  public static DataSourceStatusProvider.Status requireDataSourceStatus(BlockingQueue<DataSourceStatusProvider.Status> statuses,
      DataSourceStatusProvider.State expectedState) {
    DataSourceStatusProvider.Status status = requireDataSourceStatus(statuses);
    assertEquals(expectedState, status.getState());
    return status;
  }

  public static DataSourceStatusProvider.Status requireDataSourceStatusEventually(BlockingQueue<DataSourceStatusProvider.Status> statuses,
      DataSourceStatusProvider.State expectedState, DataSourceStatusProvider.State possibleStateBeforeThat) {
    return repeatWithTimeout(Duration.ofSeconds(2), Duration.ZERO, () -> {
      DataSourceStatusProvider.Status status = requireDataSourceStatus(statuses);
      if (status.getState() == expectedState) {
        return status;
      }
      assertEquals(possibleStateBeforeThat, status.getState());
      return null;
    });
  }
  
  public static <T> T awaitValue(BlockingQueue<T> values, Duration timeout) {
    try {
      T value = values.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
      assertNotNull("did not receive expected value within " + timeout, value);
      return value;
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
  
  public static <T> void expectNoMoreValues(BlockingQueue<T> values, Duration timeout) {
    try {
      T value = values.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
      assertNull("expected no more values", value);
    } catch (InterruptedException e) {}
  }
  
  public static <T extends FlagChangeEvent> void expectEvents(BlockingQueue<T> events, String... flagKeys) {
    Set<String> expectedChangedFlagKeys = ImmutableSet.copyOf(flagKeys);
    Set<String> actualChangedFlagKeys = new HashSet<>();
    for (int i = 0; i < expectedChangedFlagKeys.size(); i++) {
      T e = awaitValue(events, Duration.ofSeconds(1));
      actualChangedFlagKeys.add(e.getKey());
    }
    assertThat(actualChangedFlagKeys, equalTo(expectedChangedFlagKeys));
    expectNoMoreValues(events, Duration.ofMillis(100));
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
