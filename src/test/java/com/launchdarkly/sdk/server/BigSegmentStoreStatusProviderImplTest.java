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
public class BigSegmentStoreStatusProviderImplTest extends EasyMockSupport {

  // We don't need to extensively test status broadcasting behavior, just that the implementation
  // delegates to the BigSegmentStoreWrapper and EventBroadcasterImpl.

  private StatusListener mockStatusListener;
  private EventBroadcasterImpl<StatusListener, Status> mockEventBroadcaster;

  @Before
  @SuppressWarnings("unchecked")
  public void setup() {
    mockEventBroadcaster = strictMock(EventBroadcasterImpl.class);
    mockStatusListener = strictMock(StatusListener.class);
  }

  @Test
  public void statusUnavailableWithNullWrapper() {
    replayAll();
    BigSegmentStoreStatusProviderImpl statusProvider = new BigSegmentStoreStatusProviderImpl(mockEventBroadcaster, null);
    assertEquals(statusProvider.getStatus(), new Status(false, false));
    verifyAll();
  }

  @Test
  public void statusDelegatedToWrapper() {
    BigSegmentStoreWrapper storeWrapper = strictMock(BigSegmentStoreWrapper.class);
    expect(storeWrapper.getStatus()).andReturn(new Status(true, false)).once();
    replayAll();

    BigSegmentStoreStatusProviderImpl statusProvider = new BigSegmentStoreStatusProviderImpl(mockEventBroadcaster, storeWrapper);
    assertEquals(statusProvider.getStatus(), new Status(true, false));
    verifyAll();
  }

  @Test
  public void listenersDelegatedToEventBroadcaster() {
    mockEventBroadcaster.register(same(mockStatusListener));
    mockEventBroadcaster.unregister(same(mockStatusListener));
    replayAll();

    BigSegmentStoreStatusProviderImpl statusProvider = new BigSegmentStoreStatusProviderImpl(mockEventBroadcaster, null);
    statusProvider.addStatusListener(mockStatusListener);
    statusProvider.removeStatusListener(mockStatusListener);
    verifyAll();
  }
}
