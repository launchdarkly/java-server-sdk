package com.launchdarkly.sdk.server;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.same;
import static org.junit.Assert.assertEquals;

import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider.Status;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider.StatusListener;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("javadoc")
public class BigSegmentStoreStatusProviderImplTest extends BaseTest {

  // We don't need to extensively test status broadcasting behavior, just that the implementation
  // delegates to the BigSegmentStoreWrapper and EventBroadcasterImpl.

  private StatusListener mockStatusListener;
  private EventBroadcasterImpl<StatusListener, Status> mockEventBroadcaster;
  private final EasyMockSupport mocks = new EasyMockSupport();

  @Before
  @SuppressWarnings("unchecked")
  public void setup() {
    mockEventBroadcaster = mocks.strictMock(EventBroadcasterImpl.class);
    mockStatusListener = mocks.strictMock(StatusListener.class);
  }

  @Test
  public void statusUnavailableWithNullWrapper() {
    mocks.replayAll();
    BigSegmentStoreStatusProviderImpl statusProvider = new BigSegmentStoreStatusProviderImpl(mockEventBroadcaster, null);
    assertEquals(statusProvider.getStatus(), new Status(false, false));
    mocks.verifyAll();
  }

  @Test
  public void statusDelegatedToWrapper() {
    BigSegmentStoreWrapper storeWrapper = mocks.strictMock(BigSegmentStoreWrapper.class);
    expect(storeWrapper.getStatus()).andReturn(new Status(true, false)).once();
    mocks.replayAll();

    BigSegmentStoreStatusProviderImpl statusProvider = new BigSegmentStoreStatusProviderImpl(mockEventBroadcaster, storeWrapper);
    assertEquals(statusProvider.getStatus(), new Status(true, false));
    mocks.verifyAll();
  }

  @Test
  public void listenersDelegatedToEventBroadcaster() {
    mockEventBroadcaster.register(same(mockStatusListener));
    mockEventBroadcaster.unregister(same(mockStatusListener));
    mocks.replayAll();

    BigSegmentStoreStatusProviderImpl statusProvider = new BigSegmentStoreStatusProviderImpl(mockEventBroadcaster, null);
    statusProvider.addStatusListener(mockStatusListener);
    statusProvider.removeStatusListener(mockStatusListener);
    mocks.verifyAll();
  }
}
