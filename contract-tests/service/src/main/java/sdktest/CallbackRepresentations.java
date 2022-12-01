package sdktest;

import java.util.Map;

public abstract class CallbackRepresentations {
  public static class BigSegmentStoreGetMetadataResponse {
    Long lastUpToDate;
  }

  public static class BigSegmentStoreGetMembershipParams {
    String contextHash;
  }

  public static class BigSegmentStoreGetMembershipResponse {
    Map<String, Boolean> values;
  }
}
