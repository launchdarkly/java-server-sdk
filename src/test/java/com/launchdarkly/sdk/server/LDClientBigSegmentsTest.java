package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason.BigSegmentsStatus;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStore;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.Membership;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.StoreMetadata;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataStore;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static com.launchdarkly.sdk.server.BigSegmentStoreWrapper.hashForUserKey;
import static com.launchdarkly.sdk.server.Evaluator.makeBigSegmentRef;
import static com.launchdarkly.sdk.server.ModelBuilders.booleanFlagWithClauses;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingSegment;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.TestComponents.initedDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificComponent;
import static com.launchdarkly.sdk.server.TestUtil.upsertFlag;
import static com.launchdarkly.sdk.server.TestUtil.upsertSegment;
import static com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.createMembershipFromSegmentRefs;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDClientBigSegmentsTest extends BaseTest {
  private final LDContext user = LDContext.create("userkey");
  private final Segment bigSegment = segmentBuilder("segmentkey").unbounded(true).generation(1).build();
  private final FeatureFlag flag = booleanFlagWithClauses("flagkey", clauseMatchingSegment(bigSegment));

  private LDConfig.Builder configBuilder;
  private BigSegmentStore storeMock;
  private ComponentConfigurer<BigSegmentStore> storeFactoryMock;
  private final EasyMockSupport mocks = new EasyMockSupport();

  @SuppressWarnings("unchecked")
  @Before
  public void setup() {
    DataStore dataStore = initedDataStore();
    upsertFlag(dataStore, flag);
    upsertSegment(dataStore, bigSegment);

    storeMock = mocks.niceMock(BigSegmentStore.class);
    storeFactoryMock = mocks.strictMock(ComponentConfigurer.class);
    expect(storeFactoryMock.build(isA(ClientContext.class))).andReturn(storeMock);

    configBuilder = baseConfig().dataStore(specificComponent(dataStore));
  }

  @Test
  public void userNotFound() throws Exception {
    expect(storeMock.getMetadata()).andAnswer(() -> new StoreMetadata(System.currentTimeMillis())).anyTimes();
    expect(storeMock.getMembership(hashForUserKey(user.getKey()))).andReturn(null);
    mocks.replayAll();

    LDConfig config = configBuilder.bigSegments(Components.bigSegments(storeFactoryMock)).build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      EvaluationDetail<Boolean> result = client.boolVariationDetail("flagkey", user, false);
      assertFalse(result.getValue());
      assertEquals(BigSegmentsStatus.HEALTHY, result.getReason().getBigSegmentsStatus());
    }
  }

  @Test
  public void userFound() throws Exception {
    Membership membership = createMembershipFromSegmentRefs(Collections.singleton(makeBigSegmentRef(bigSegment)), null);
    expect(storeMock.getMetadata()).andAnswer(() -> new StoreMetadata(System.currentTimeMillis())).anyTimes();
    expect(storeMock.getMembership(hashForUserKey(user.getKey()))).andReturn(membership);
    mocks.replayAll();

    LDConfig config = configBuilder.bigSegments(Components.bigSegments(storeFactoryMock)).build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      EvaluationDetail<Boolean> result = client.boolVariationDetail("flagkey", user, false);
      assertTrue(result.getValue());
      assertEquals(BigSegmentsStatus.HEALTHY, result.getReason().getBigSegmentsStatus());
    }
  }

  @Test
  public void storeError() throws Exception {
    expect(storeMock.getMetadata()).andAnswer(() -> new StoreMetadata(System.currentTimeMillis())).anyTimes();
    expect(storeMock.getMembership(hashForUserKey(user.getKey()))).andThrow(new RuntimeException("sorry"));
    mocks.replayAll();

    LDConfig config = configBuilder.bigSegments(Components.bigSegments(storeFactoryMock)).build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      EvaluationDetail<Boolean> result = client.boolVariationDetail("flagkey", user, false);
      assertFalse(result.getValue());
      assertEquals(BigSegmentsStatus.STORE_ERROR, result.getReason().getBigSegmentsStatus());
    }
  }

  @Test
  public void storeNotConfigured() throws Exception {
    try (LDClient client = new LDClient("SDK_KEY", configBuilder.build())) {
      EvaluationDetail<Boolean> result = client.boolVariationDetail("flagkey", user, false);
      assertFalse(result.getValue());
      assertEquals(BigSegmentsStatus.NOT_CONFIGURED, result.getReason().getBigSegmentsStatus());
    }
  }
}
