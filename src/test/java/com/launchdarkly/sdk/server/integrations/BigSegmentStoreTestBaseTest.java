package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.subsystems.BigSegmentStore;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.Membership;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.StoreMetadata;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.createMembershipFromSegmentRefs;

@SuppressWarnings("javadoc")
public class BigSegmentStoreTestBaseTest extends BigSegmentStoreTestBase {
  // This runs BigSegmentStoreTestBase against a mock store implementation that is known to behave
  // as expected, to verify that the test suite logic has the correct expectations.

  private static class DataSet {
    StoreMetadata metadata = null;
    Map<String, Membership> memberships = new HashMap<>();
  }

  private final Map<String, DataSet> allData = new HashMap<>();

  private DataSet getOrCreateDataSet(String prefix) {
    allData.putIfAbsent(prefix, new DataSet());
    return allData.get(prefix);
  }

  @Override
  protected ComponentConfigurer<BigSegmentStore> makeStore(String prefix) {
    return new MockStoreFactory(getOrCreateDataSet(prefix));
  }

  @Override
  protected void clearData(String prefix) {
    DataSet dataSet = getOrCreateDataSet(prefix);
    dataSet.metadata = null;
    dataSet.memberships.clear();
  }

  @Override
  protected void setMetadata(String prefix, StoreMetadata metadata) {
    DataSet dataSet = getOrCreateDataSet(prefix);
    dataSet.metadata = metadata;
  }

  @Override
  protected void setSegments(String prefix, String userHashKey, Iterable<String> includedSegmentRefs, Iterable<String> excludedSegmentRefs) {
    DataSet dataSet = getOrCreateDataSet(prefix);
    dataSet.memberships.put(userHashKey, createMembershipFromSegmentRefs(includedSegmentRefs, excludedSegmentRefs));
  }

  private static final class MockStoreFactory implements ComponentConfigurer<BigSegmentStore> {
    private final DataSet data;

    private MockStoreFactory(DataSet data) {
      this.data = data;
    }

    @Override
    public BigSegmentStore build(ClientContext context) {
      return new MockStore(data);
    }
  }

  private static final class MockStore implements BigSegmentStore {
    private final DataSet data;

    private MockStore(DataSet data) {
      this.data = data;
    }

    @Override
    public Membership getMembership(String userHash) {
      return data.memberships.get(userHash);
    }

    @Override
    public StoreMetadata getMetadata() {
      return data.metadata;
    }

    @Override
    public void close() throws IOException { }
  }
}
