package com.launchdarkly.sdk.server;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.VersionedData;
import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.testhelpers.Assertions;
import com.launchdarkly.testhelpers.JsonAssertions;
import com.launchdarkly.testhelpers.httptest.SpecialHttpConfigurations;

import java.io.StringReader;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toDataMap;
import static com.launchdarkly.sdk.server.JsonHelpers.serialize;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.assertNoMoreValues;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.awaitValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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
  
  public static void assertJsonEquals(LDValue expected, LDValue actual) {
    // Gives a better failure diff than assertEquals
    JsonAssertions.assertJsonEquals(expected.toJsonString(), actual.toJsonString());
  }
  
  public static void upsertFlag(DataStore store, FeatureFlag flag) {
    store.upsert(FEATURES, flag.getKey(), new ItemDescriptor(flag.getVersion(), flag));
  }
  
  public static void upsertSegment(DataStore store, Segment segment) {
    store.upsert(SEGMENTS, segment.getKey(), new ItemDescriptor(segment.getVersion(), segment));
  }

  public static DataSourceStatusProvider.Status requireDataSourceStatus(BlockingQueue<DataSourceStatusProvider.Status> statuses, Duration timeout) {
    return awaitValue(statuses, timeout.toMillis(), TimeUnit.MILLISECONDS);
  }
  
  public static DataSourceStatusProvider.Status requireDataSourceStatus(BlockingQueue<DataSourceStatusProvider.Status> statuses) {
    return requireDataSourceStatus(statuses, Duration.ofSeconds(5));
    // Using a fairly long default timeout here because there can be unpredictable execution delays
    // in CI. If there's a test where we specifically need to enforce a smaller timeout, we can set
    // that explicitly on a per-call basis.
  }
  
  public static DataSourceStatusProvider.Status requireDataSourceStatus(BlockingQueue<DataSourceStatusProvider.Status> statuses,
      DataSourceStatusProvider.State expectedState, Duration timeout) {
    DataSourceStatusProvider.Status status = requireDataSourceStatus(statuses, timeout);
    assertEquals(expectedState, status.getState());
    return status;
  }

  public static DataSourceStatusProvider.Status requireDataSourceStatus(BlockingQueue<DataSourceStatusProvider.Status> statuses,
      DataSourceStatusProvider.State expectedState) {
    return requireDataSourceStatus(statuses, expectedState, Duration.ofSeconds(5));
  }

  public static DataSourceStatusProvider.Status requireDataSourceStatusEventually(BlockingQueue<DataSourceStatusProvider.Status> statuses,
      DataSourceStatusProvider.State expectedState, DataSourceStatusProvider.State possibleStateBeforeThat) {
    return Assertions.assertPolledFunctionReturnsValue(2, TimeUnit.SECONDS, 0, null, () -> {
      DataSourceStatusProvider.Status status = requireDataSourceStatus(statuses);
      if (status.getState() == expectedState) {
        return status;
      }
      assertEquals(possibleStateBeforeThat, status.getState());
      return null;
    });
  }
  
  public static void assertDataSetEquals(FullDataSet<ItemDescriptor> expected, FullDataSet<ItemDescriptor> actual) {
    String expectedJson = TEST_GSON_INSTANCE.toJson(toDataMap(expected));
    String actualJson = TEST_GSON_INSTANCE.toJson(toDataMap(actual));
    JsonAssertions.assertJsonEquals(expectedJson, actualJson);
  }
  
  public static void assertItemEquals(VersionedData expected, ItemDescriptor item) {
    assertEquals(expected.getVersion(), item.getVersion());
    assertEquals(expected.getClass(), item.getItem().getClass());
    JsonAssertions.assertJsonEquals(serialize(expected), serialize(item.getItem()));
  }
  
  public static String describeDataSet(FullDataSet<ItemDescriptor> data) {
    return Joiner.on(", ").join(
        Iterables.transform(data.getData(), entry -> {
          DataKind kind = entry.getKey();
          return "{" + kind + ": [" +
            Joiner.on(", ").join(
                Iterables.transform(entry.getValue().getItems(), item ->
                  kind.serialize(item.getValue())
                  )
                ) +
              "]}";
        }));
  }
  
  public static JsonReader jsonReaderFrom(String data) {
    return new JsonReader(new StringReader(data));
  }
  
  public static interface ActionCanThrowAnyException<T> {
    void apply(T param) throws Exception;
  }
  
  public static <T extends FlagChangeEvent> void expectEvents(BlockingQueue<T> events, String... flagKeys) {
    Set<String> expectedChangedFlagKeys = ImmutableSet.copyOf(flagKeys);
    Set<String> actualChangedFlagKeys = new HashSet<>();
    for (int i = 0; i < expectedChangedFlagKeys.size(); i++) {
      T e = awaitValue(events, 1, TimeUnit.SECONDS);
      actualChangedFlagKeys.add(e.getKey());
    }
    assertThat(actualChangedFlagKeys, equalTo(expectedChangedFlagKeys));
    assertNoMoreValues(events, 100, TimeUnit.MILLISECONDS);
  }
  
  public static EvalResult simpleEvaluation(int variation, LDValue value) {
    return EvalResult.of(value, variation, EvaluationReason.fallthrough());
  }
  
  public static <T> void assertFullyEqual(T a, T b) {
    assertEquals(a, b);
    assertEquals(b, a);
    assertEquals(a.hashCode(), b.hashCode());
  }

  public static <T> void assertFullyUnequal(T a, T b) {
    assertNotEquals(a, b);
    assertNotEquals(b, a);
  }

  public static void assertThrows(Class<?> exceptionClass, Runnable r) {
    try {
      r.run();
      fail("expected exception");
    } catch (RuntimeException e) {
      assertThat(e.getClass(), equalTo(exceptionClass));
    }
  }
  
  public static HttpConfigurationBuilder makeHttpConfigurationFromTestParams(
      SpecialHttpConfigurations.Params params) {
    HttpConfigurationBuilder b = Components.httpConfiguration();
    if (params.getTlsConfig() != null) {
      b.sslSocketFactory(params.getTlsConfig().getSocketFactory(), params.getTlsConfig().getTrustManager());
    }
    if (params.getProxyHost() != null) {
      b.proxyHostAndPort(params.getProxyHost(), params.getProxyPort());
      if (params.getProxyBasicAuthUser() != null) {
        b.proxyAuth(Components.httpBasicAuthentication(params.getProxyBasicAuthUser(), params.getProxyBasicAuthPassword()));
      }
    }
    if (params.getSocketFactory() != null) {
      b.socketFactory(params.getSocketFactory());
    }
    return b;
  }
  
  public interface BuilderPropertyTester<TValue> {
    void assertDefault(TValue defaultValue);
    void assertCanSet(TValue newValue);
    void assertSetIsChangedTo(TValue attempted, TValue resulting);
  }

  public static class BuilderTestUtil<TBuilder, TBuilt> {
    private final Supplier<TBuilder> constructor;
    final Function<TBuilder, TBuilt> buildMethod;

    public BuilderTestUtil(Supplier<TBuilder> constructor,
                           Function<TBuilder, TBuilt> buildMethod) {
      this.constructor = constructor;
      this.buildMethod = buildMethod;
    }

    public <TValue> BuilderPropertyTester<TValue> property(
        Function<TBuilt, TValue> getter,
        BiConsumer<TBuilder, TValue> setter) {
      return new BuilderPropertyTestImpl<TBuilder, TBuilt, TValue>(this, getter, setter);
    }

    public TBuilder createBuilder() {
      return constructor.get();
    }
  }

  static class BuilderPropertyTestImpl<TBuilder, TBuilt, TValue>
      implements BuilderPropertyTester<TValue> {
    private final BuilderTestUtil<TBuilder, TBuilt> owner;
    private final Function<TBuilt, TValue> getter;
    private final BiConsumer<TBuilder, TValue> setter;

    public BuilderPropertyTestImpl(BuilderTestUtil<TBuilder, TBuilt> owner,
                                   Function<TBuilt, TValue> getter,
                                   BiConsumer<TBuilder, TValue> setter) {
      this.owner = owner;
      this.getter = getter;
      this.setter = setter;
    }

    public void assertDefault(TValue defaultValue) {
      assertValue(owner.createBuilder(), defaultValue);
    }

    public void assertCanSet(TValue newValue) {
      assertSetIsChangedTo(newValue, newValue);
    }

    public void assertSetIsChangedTo(TValue attempted, TValue resulting) {
      TBuilder builder = owner.createBuilder();
      setter.accept(builder, attempted);
      assertValue(builder, resulting);
    }

    private void assertValue(TBuilder b, TValue expected) {
      TBuilt built = owner.buildMethod.apply(b);
      assertEquals(expected, getter.apply(built));
    }
  }
}
