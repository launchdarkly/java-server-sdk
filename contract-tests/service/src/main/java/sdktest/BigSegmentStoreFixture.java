package sdktest;

import com.launchdarkly.sdk.server.interfaces.BigSegmentStore;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreFactory;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreTypes.Membership;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreTypes.StoreMetadata;
import com.launchdarkly.sdk.server.interfaces.ClientContext;

import java.io.IOException;

import sdktest.CallbackRepresentations.BigSegmentStoreGetMembershipParams;
import sdktest.CallbackRepresentations.BigSegmentStoreGetMembershipResponse;
import sdktest.CallbackRepresentations.BigSegmentStoreGetMetadataResponse;

public class BigSegmentStoreFixture implements BigSegmentStore, BigSegmentStoreFactory {
  private final CallbackService service;
  
  public BigSegmentStoreFixture(CallbackService service) {
    this.service = service;
  }
  
  @Override
  public void close() throws IOException {
    service.close();
  }

  @Override
  public Membership getMembership(String userHash) {
    BigSegmentStoreGetMembershipParams params = new BigSegmentStoreGetMembershipParams();
    params.userHash = userHash;
    BigSegmentStoreGetMembershipResponse resp =
        service.post("/getMembership", params, BigSegmentStoreGetMembershipResponse.class);
    return new Membership() {
      @Override
      public Boolean checkMembership(String segmentRef) {
        return resp.values == null ? null : resp.values.get(segmentRef);
      }
    };
  }

  @Override
  public StoreMetadata getMetadata() {
    BigSegmentStoreGetMetadataResponse resp =
        service.post("/getMetadata", null, BigSegmentStoreGetMetadataResponse.class);
    return new StoreMetadata(resp.lastUpToDate);
  }

  @Override
  public BigSegmentStore createBigSegmentStore(ClientContext context) {
    return this;
  }
}
