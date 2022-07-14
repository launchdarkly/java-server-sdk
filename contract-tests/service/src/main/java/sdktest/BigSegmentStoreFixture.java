package sdktest;

import com.launchdarkly.sdk.server.subsystems.BigSegmentStore;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreFactory;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.Membership;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.StoreMetadata;

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
  public Membership getMembership(String contextHash) {
    BigSegmentStoreGetMembershipParams params = new BigSegmentStoreGetMembershipParams();
    params.contextHash = contextHash;
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
