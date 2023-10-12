package sdktest;

import com.launchdarkly.sdk.server.subsystems.BigSegmentStore;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.Membership;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.StoreMetadata;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;

import java.io.IOException;

import sdktest.BigSegmentCallbackRepresentation.BigSegmentStoreGetMembershipParams;
import sdktest.BigSegmentCallbackRepresentation.BigSegmentStoreGetMembershipResponse;
import sdktest.BigSegmentCallbackRepresentation.BigSegmentStoreGetMetadataResponse;

public class BigSegmentStoreFixture implements BigSegmentStore, ComponentConfigurer<BigSegmentStore> {
  private final BigSegmentCallbackService service;
  
  public BigSegmentStoreFixture(BigSegmentCallbackService service) {
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
  public BigSegmentStore build(ClientContext context) {
    return this;
  }
}
