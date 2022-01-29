package com.launchdarkly.sdk.server.integrations;

import static com.launchdarkly.sdk.server.TestUtil.BuilderPropertyTester;
import static com.launchdarkly.sdk.server.TestUtil.BuilderTestUtil;
import static org.easymock.EasyMock.createStrictControl;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertSame;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStore;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreFactory;
import com.launchdarkly.sdk.server.interfaces.BigSegmentsConfiguration;
import com.launchdarkly.sdk.server.interfaces.ClientContext;

import org.easymock.IMocksControl;
import org.junit.Test;

import java.time.Duration;

@SuppressWarnings("javadoc")
public class BigSegmentsConfigurationBuilderTest {

  private final BuilderTestUtil<BigSegmentsConfigurationBuilder, BigSegmentsConfiguration> tester;

  public BigSegmentsConfigurationBuilderTest() {
    tester = new BuilderTestUtil<>(() -> Components.bigSegments(null),
                                   b -> b.createBigSegmentsConfiguration(null));
  }

  @Test
  public void storeFactory() {
    IMocksControl ctrl = createStrictControl();
    ClientContext contextMock = ctrl.createMock(ClientContext.class);
    BigSegmentStore storeMock = ctrl.createMock(BigSegmentStore.class);
    BigSegmentStoreFactory storeFactoryMock = ctrl.createMock(BigSegmentStoreFactory.class);

    storeFactoryMock.createBigSegmentStore(contextMock);
    expectLastCall().andReturn(storeMock);
    ctrl.replay();

    BigSegmentsConfigurationBuilder b = Components.bigSegments(storeFactoryMock);
    BigSegmentsConfiguration c = b.createBigSegmentsConfiguration(contextMock);

    assertSame(storeMock, c.getStore());
    ctrl.verify();
  }

  @Test
  public void userCacheSize() {
    BuilderPropertyTester<Integer> prop = tester.property(BigSegmentsConfiguration::getUserCacheSize,
                                                          BigSegmentsConfigurationBuilder::userCacheSize);
    prop.assertDefault(BigSegmentsConfigurationBuilder.DEFAULT_USER_CACHE_SIZE);
    prop.assertCanSet(500);
    prop.assertCanSet(0);
    prop.assertSetIsChangedTo(-1, 0);
  }

  @Test
  public void userCacheTime() {
    BuilderPropertyTester<Duration> prop = tester.property(BigSegmentsConfiguration::getUserCacheTime,
                                                           BigSegmentsConfigurationBuilder::userCacheTime);
    prop.assertDefault(BigSegmentsConfigurationBuilder.DEFAULT_USER_CACHE_TIME);
    prop.assertCanSet(Duration.ofSeconds(10));
    prop.assertSetIsChangedTo(null, BigSegmentsConfigurationBuilder.DEFAULT_USER_CACHE_TIME);
    prop.assertSetIsChangedTo(Duration.ofSeconds(-1), BigSegmentsConfigurationBuilder.DEFAULT_USER_CACHE_TIME);
  }

  @Test
  public void statusPollInterval() {
    BuilderPropertyTester<Duration> prop = tester.property(BigSegmentsConfiguration::getStatusPollInterval,
                                                           BigSegmentsConfigurationBuilder::statusPollInterval);
    prop.assertDefault(BigSegmentsConfigurationBuilder.DEFAULT_STATUS_POLL_INTERVAL);
    prop.assertCanSet(Duration.ofSeconds(10));
    prop.assertSetIsChangedTo(null, BigSegmentsConfigurationBuilder.DEFAULT_STATUS_POLL_INTERVAL);
    prop.assertSetIsChangedTo(Duration.ofSeconds(-1), BigSegmentsConfigurationBuilder.DEFAULT_STATUS_POLL_INTERVAL);
  }

  @Test
  public void staleAfter() {
    BuilderPropertyTester<Duration> prop = tester.property(BigSegmentsConfiguration::getStaleAfter,
                                                           BigSegmentsConfigurationBuilder::staleAfter);
    prop.assertDefault(BigSegmentsConfigurationBuilder.DEFAULT_STALE_AFTER);
    prop.assertCanSet(Duration.ofSeconds(10));
    prop.assertSetIsChangedTo(null, BigSegmentsConfigurationBuilder.DEFAULT_STALE_AFTER);
    prop.assertSetIsChangedTo(Duration.ofSeconds(-1), BigSegmentsConfigurationBuilder.DEFAULT_STALE_AFTER);
  }
}
