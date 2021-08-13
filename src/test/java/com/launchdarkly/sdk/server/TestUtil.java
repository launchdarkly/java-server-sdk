package com.launchdarkly.sdk.server;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.testhelpers.Assertions;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toDataMap;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.assertNoMoreValues;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.awaitValue;
import static com.launchdarkly.testhelpers.JsonAssertions.assertJsonEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

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
  
  public static void upsertFlag(DataStore store, FeatureFlag flag) {
    store.upsert(FEATURES, flag.getKey(), new ItemDescriptor(flag.getVersion(), flag));
  }
  
  public static void upsertSegment(DataStore store, Segment segment) {
    store.upsert(SEGMENTS, segment.getKey(), new ItemDescriptor(segment.getVersion(), segment));
  }

  public static DataSourceStatusProvider.Status requireDataSourceStatus(BlockingQueue<DataSourceStatusProvider.Status> statuses) {
    return awaitValue(statuses, 1, TimeUnit.SECONDS);
  }
  
  public static DataSourceStatusProvider.Status requireDataSourceStatus(BlockingQueue<DataSourceStatusProvider.Status> statuses,
      DataSourceStatusProvider.State expectedState) {
    DataSourceStatusProvider.Status status = requireDataSourceStatus(statuses);
    assertEquals(expectedState, status.getState());
    return status;
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
    assertJsonEquals(expectedJson, actualJson);
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
  
  public static Evaluator.EvalResult simpleEvaluation(int variation, LDValue value) {
    return new Evaluator.EvalResult(value, variation, EvaluationReason.fallthrough());
  }
  
  // returns a socket factory that creates sockets that only connect to host and port
  static SocketFactorySingleHost makeSocketFactorySingleHost(String host, int port) {
    return new SocketFactorySingleHost(host, port);
  }
  
  private static final class SocketSingleHost extends Socket {
    private final String host;
    private final int port;

    SocketSingleHost(String host, int port) {
      this.host = host;
      this.port = port;
    }

    @Override public void connect(SocketAddress endpoint) throws IOException {
      super.connect(new InetSocketAddress(this.host, this.port), 0);
    }

    @Override public void connect(SocketAddress endpoint, int timeout) throws IOException {
      super.connect(new InetSocketAddress(this.host, this.port), timeout);
    }
  }

  public static final class SocketFactorySingleHost extends SocketFactory {
    private final String host;
    private final int port;

    public SocketFactorySingleHost(String host, int port) {
      this.host = host;
      this.port = port;
    }

    @Override public Socket createSocket() throws IOException {
      return new SocketSingleHost(this.host, this.port);
    }

    @Override public Socket createSocket(String host, int port) throws IOException {
      Socket socket = createSocket();
      socket.connect(new InetSocketAddress(this.host, this.port));
      return socket;
    }

    @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
      Socket socket = createSocket();
      socket.connect(new InetSocketAddress(this.host, this.port));
      return socket;
    }

    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
      Socket socket = createSocket();
      socket.connect(new InetSocketAddress(this.host, this.port));
      return socket;
    }

    @Override public Socket createSocket(InetAddress host, int port, InetAddress localAddress, int localPort) throws IOException {
      Socket socket = createSocket();
      socket.connect(new InetSocketAddress(this.host, this.port));
      return socket;
    }
  }
}
