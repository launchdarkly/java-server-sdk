package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason.BigSegmentsStatus;
import com.launchdarkly.sdk.server.BigSegmentStoreWrapper.BigSegmentsQueryResult;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider.Status;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider.StatusListener;
import com.launchdarkly.sdk.server.interfaces.BigSegmentsConfiguration;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStore;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.Membership;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.StoreMetadata;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestComponents.nullLogger;
import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.createMembershipFromSegmentRefs;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class BigSegmentStoreWrapperTest extends BaseTest {
  private static final String SDK_KEY = "sdk-key";

  private final EasyMockSupport mocks = new EasyMockSupport();
  private AtomicBoolean storeUnavailable;
  private AtomicReference<StoreMetadata> storeMetadata;
  private BigSegmentStore storeMock;
  private ComponentConfigurer<BigSegmentStore> storeFactoryMock;
  private EventBroadcasterImpl<StatusListener, Status> eventBroadcaster;

  @SuppressWarnings("unchecked")
  @Before
  public void setup() {
    eventBroadcaster = EventBroadcasterImpl.forBigSegmentStoreStatus(sharedExecutor, nullLogger);
    storeUnavailable = new AtomicBoolean(false);
    storeMetadata = new AtomicReference<>(null);
    storeMock = mocks.niceMock(BigSegmentStore.class);
    expect(storeMock.getMetadata()).andAnswer(() -> {
      if (storeUnavailable.get()) {
        throw new RuntimeException("sorry");
      }
      return storeMetadata.get();
    }).anyTimes();
    storeFactoryMock = mocks.strictMock(ComponentConfigurer.class);
    expect(storeFactoryMock.build(isA(ClientContext.class))).andReturn(storeMock);
  }

  private BigSegmentStoreWrapper makeWrapper(BigSegmentsConfiguration bsConfig) {
    return new BigSegmentStoreWrapper(bsConfig, eventBroadcaster, sharedExecutor, testLogger);
  }
  
  private void setStoreMembership(String userKey, Membership membership) {
    expect(storeMock.getMembership(BigSegmentStoreWrapper.hashForUserKey(userKey))).andReturn(membership);
  }

  @Test
  public void membershipQueryWithUncachedResultAndHealthyStatus() throws Exception {
    Membership expectedMembership = createMembershipFromSegmentRefs(Collections.singleton("key1"), Collections.singleton("key2"));

    String userKey = "userkey";
    setStoreMembership(userKey, expectedMembership);
    mocks.replayAll();

    storeMetadata.set(new StoreMetadata(System.currentTimeMillis()));
    BigSegmentsConfiguration bsConfig = Components.bigSegments(storeFactoryMock)
        .staleAfter(Duration.ofDays(1))
        .build(clientContext(SDK_KEY, new LDConfig.Builder().build()));
    try (BigSegmentStoreWrapper wrapper = makeWrapper(bsConfig)) {
      BigSegmentsQueryResult res = wrapper.getUserMembership(userKey);
      assertEquals(expectedMembership, res.membership);
      assertEquals(BigSegmentsStatus.HEALTHY, res.status);
    }
  }

  @Test
  public void membershipQueryReturnsNull() throws Exception {
    String userKey = "userkey";
    setStoreMembership(userKey, null);
    mocks.replayAll();

    storeMetadata.set(new StoreMetadata(System.currentTimeMillis()));
    BigSegmentsConfiguration bsConfig = Components.bigSegments(storeFactoryMock)
        .staleAfter(Duration.ofDays(1))
        .build(clientContext(SDK_KEY, new LDConfig.Builder().build()));
    try (BigSegmentStoreWrapper wrapper = makeWrapper(bsConfig)) {
      BigSegmentsQueryResult res = wrapper.getUserMembership(userKey);
      assertEquals(createMembershipFromSegmentRefs(null, null), res.membership);
      assertEquals(BigSegmentsStatus.HEALTHY, res.status);
    }
  }

  @Test
  public void membershipQueryWithCachedResultAndHealthyStatus() throws Exception {
    Membership expectedMembership = createMembershipFromSegmentRefs(Collections.singleton("key1"), Collections.singleton("key2"));
    String userKey = "userkey";
    setStoreMembership(userKey, expectedMembership);

    mocks.replayAll();

    storeMetadata.set(new StoreMetadata(System.currentTimeMillis()));
    BigSegmentsConfiguration bsConfig = Components.bigSegments(storeFactoryMock)
        .staleAfter(Duration.ofDays(1))
        .build(clientContext(SDK_KEY, new LDConfig.Builder().build()));
    try (BigSegmentStoreWrapper wrapper = makeWrapper(bsConfig)) {
      BigSegmentsQueryResult res1 = wrapper.getUserMembership(userKey);
      assertEquals(expectedMembership, res1.membership);
      assertEquals(BigSegmentsStatus.HEALTHY, res1.status);

      BigSegmentsQueryResult res2 = wrapper.getUserMembership(userKey);
      assertEquals(expectedMembership, res2.membership);
      assertEquals(BigSegmentsStatus.HEALTHY, res2.status);
    }
  }

  @Test
  public void membershipQueryWithStaleStatus() throws Exception {
    Membership expectedMembership = createMembershipFromSegmentRefs(Collections.singleton("key1"), Collections.singleton("key2"));
    String userKey = "userkey";
    setStoreMembership(userKey, expectedMembership);

    mocks.replayAll();

    storeMetadata.set(new StoreMetadata(System.currentTimeMillis() - 1000));
    BigSegmentsConfiguration bsConfig = Components.bigSegments(storeFactoryMock)
        .staleAfter(Duration.ofMillis(500))
        .build(clientContext(SDK_KEY, new LDConfig.Builder().build()));
    try (BigSegmentStoreWrapper wrapper = makeWrapper(bsConfig)) {
      BigSegmentsQueryResult res = wrapper.getUserMembership(userKey);
      assertEquals(expectedMembership, res.membership);
      assertEquals(BigSegmentsStatus.STALE, res.status);
    }
  }

  @Test
  public void membershipQueryWithStaleStatusDueToNoStoreMetadata() throws Exception {
    Membership expectedMembership = createMembershipFromSegmentRefs(Collections.singleton("key1"), Collections.singleton("key2"));
    String userKey = "userkey";
    setStoreMembership(userKey, expectedMembership);

    mocks.replayAll();

    storeMetadata.set(null);
    BigSegmentsConfiguration bsConfig = Components.bigSegments(storeFactoryMock)
        .staleAfter(Duration.ofMillis(500))
        .build(clientContext(SDK_KEY, new LDConfig.Builder().build()));
    try (BigSegmentStoreWrapper wrapper = makeWrapper(bsConfig)) {
      BigSegmentsQueryResult res = wrapper.getUserMembership(userKey);
      assertEquals(expectedMembership, res.membership);
      assertEquals(BigSegmentsStatus.STALE, res.status);
    }
  }

  @Test
  public void leastRecentUserIsEvictedFromCache() throws Exception {
    String userKey1 = "userkey1", userKey2 = "userkey2", userKey3 = "userkey3";
    Membership expectedMembership1 = createMembershipFromSegmentRefs(Collections.singleton("seg1"), null);
    Membership expectedMembership2 = createMembershipFromSegmentRefs(Collections.singleton("seg2"), null);
    Membership expectedMembership3 = createMembershipFromSegmentRefs(Collections.singleton("seg3"), null);
    setStoreMembership(userKey1, expectedMembership1);
    setStoreMembership(userKey2, expectedMembership2);
    setStoreMembership(userKey3, expectedMembership3);
    setStoreMembership(userKey1, expectedMembership1);

    mocks.replayAll();

    storeMetadata.set(new StoreMetadata(System.currentTimeMillis()));
    BigSegmentsConfiguration bsConfig = Components.bigSegments(storeFactoryMock)
        .userCacheSize(2)
        .staleAfter(Duration.ofDays(1))
        .build(clientContext(SDK_KEY, new LDConfig.Builder().build()));
    try (BigSegmentStoreWrapper wrapper = makeWrapper(bsConfig)) {
      BigSegmentsQueryResult res1 = wrapper.getUserMembership(userKey1);
      assertEquals(expectedMembership1, res1.membership);
      assertEquals(BigSegmentsStatus.HEALTHY, res1.status);

      BigSegmentsQueryResult res2 = wrapper.getUserMembership(userKey2);
      assertEquals(expectedMembership2, res2.membership);
      assertEquals(BigSegmentsStatus.HEALTHY, res2.status);

      BigSegmentsQueryResult res3 = wrapper.getUserMembership(userKey3);
      assertEquals(expectedMembership3, res3.membership);
      assertEquals(BigSegmentsStatus.HEALTHY, res3.status);

      BigSegmentsQueryResult res2a = wrapper.getUserMembership(userKey2);
      assertEquals(expectedMembership2, res2a.membership);
      assertEquals(BigSegmentsStatus.HEALTHY, res2a.status);

      BigSegmentsQueryResult res3a = wrapper.getUserMembership(userKey3);
      assertEquals(expectedMembership3, res3a.membership);
      assertEquals(BigSegmentsStatus.HEALTHY, res3a.status);

      BigSegmentsQueryResult res1a = wrapper.getUserMembership(userKey1);
      assertEquals(expectedMembership1, res1a.membership);
      assertEquals(BigSegmentsStatus.HEALTHY, res1a.status);
    }
  }

  @Test
  public void pollingDetectsStoreUnavailability() throws Exception {
    mocks.replayAll();

    storeMetadata.set(new StoreMetadata(System.currentTimeMillis()));
    BigSegmentsConfiguration bsConfig = Components.bigSegments(storeFactoryMock)
        .statusPollInterval(Duration.ofMillis(10))
        .staleAfter(Duration.ofDays(1))
        .build(clientContext(SDK_KEY, new LDConfig.Builder().build()));
    try (BigSegmentStoreWrapper wrapper = makeWrapper(bsConfig)) {
      assertTrue(wrapper.getStatus().isAvailable());

      BlockingQueue<BigSegmentStoreStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
      eventBroadcaster.register(statuses::add);

      storeUnavailable.set(true);
      Status status1 = statuses.take();
      // Depending on timing, the listener might or might not receive an initial status that is still
      // available prior to the one that is unavailable.
      if (status1.isAvailable()) {
        status1 = statuses.take();
      }
      assertFalse(status1.isAvailable());
      assertEquals(status1, wrapper.getStatus());

      storeUnavailable.set(false);
      Status status2 = statuses.take();
      assertTrue(status2.isAvailable());
      assertEquals(status2, wrapper.getStatus());
    }
  }

  @Test
  public void pollingDetectsStaleStatus() throws Exception {
    mocks.replayAll();

    storeMetadata.set(new StoreMetadata(System.currentTimeMillis() + 10000));
    BigSegmentsConfiguration bsConfig = Components.bigSegments(storeFactoryMock)
        .statusPollInterval(Duration.ofMillis(10))
        .staleAfter(Duration.ofMillis(200))
        .build(clientContext(SDK_KEY, new LDConfig.Builder().build()));
    try (BigSegmentStoreWrapper wrapper = makeWrapper(bsConfig)) {
      assertFalse(wrapper.getStatus().isStale());

      BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
      eventBroadcaster.register(statuses::add);

      storeMetadata.set(new StoreMetadata(System.currentTimeMillis() - 1000));
      // Depending on timing, the listener might or might not receive an initial status that is not
      // stale prior to the one that is stale.
      Status status1 = statuses.take();
      if (!status1.isStale()) {
        status1 = statuses.take();
      }
      assertTrue(status1.isStale());
      assertEquals(status1, wrapper.getStatus());

      storeMetadata.set(new StoreMetadata(System.currentTimeMillis() + 10000));
      Status status2 = statuses.take();
      assertFalse(status2.isStale());
      assertEquals(status2, wrapper.getStatus());
    }
  }
}
