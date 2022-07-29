package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.BaseTest;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStore;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.Membership;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.StoreMetadata;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.createMembershipFromSegmentRefs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * A configurable test class for all implementations of {@link BigSegmentStore}.
 * <p>
 * Each implementation of {@link BigSegmentStore} should define a test class that is a subclass of
 * this class for their implementation type, and run it in the unit tests for their project.
 * <p>
 * The tests are configured for the details specific to the implementation type by overriding the
 * abstract methods {@link #makeStore(String)}, {@link #clearData(String)},
 * {@link #setMetadata(String, StoreMetadata)}, and {@link #setSegments(String, String, Iterable, Iterable)}.
 */
@SuppressWarnings("javadoc")
public abstract class BigSegmentStoreTestBase extends BaseTest {
  private static final String prefix = "testprefix";
  private static final String fakeUserHash = "userhash";
  private static final String segmentRef1 = "key1", segmentRef2 = "key2", segmentRef3 = "key3";
  private static final String[] allSegmentRefs = {segmentRef1, segmentRef2, segmentRef3};

  private ClientContext makeClientContext() {
    return clientContext("", baseConfig().build());
  }
  
  private BigSegmentStore makeEmptyStore() throws Exception {
    BigSegmentStore store = makeStore(prefix).build(makeClientContext());
    try {
      clearData(prefix);
    } catch (RuntimeException ex) {
      store.close();
      throw ex;
    }
    return store;
  }

  @Test
  public void missingMetadata() throws Exception {
    try (BigSegmentStore store = makeEmptyStore()) {
      assertNull(store.getMetadata());
    }
  }

  @Test
  public void validMetadata() throws Exception {
    try (BigSegmentStore store = makeEmptyStore()) {
      StoreMetadata metadata = new StoreMetadata(System.currentTimeMillis());
      setMetadata(prefix, metadata);

      StoreMetadata result = store.getMetadata();
      assertNotNull(result);
      assertEquals(metadata.getLastUpToDate(), result.getLastUpToDate());
    }
  }

  @Test
  public void membershipNotFound() throws Exception {
    try (BigSegmentStore store = makeEmptyStore()) {
      Membership membership = store.getMembership(fakeUserHash);

      // Either null or an empty membership is allowed
      if (membership != null) {
        assertEqualMembership(createMembershipFromSegmentRefs(null, null), membership);
      }
    }
  }

  @Test
  public void membershipFound() throws Exception {
    List<Memberships> membershipsList = Arrays.asList(
        new Memberships(Collections.singleton(segmentRef1), null),
        new Memberships(Arrays.asList(segmentRef1, segmentRef2), null),
        new Memberships(null, Collections.singleton(segmentRef1)),
        new Memberships(null, Arrays.asList(segmentRef1, segmentRef2)),
        new Memberships(Arrays.asList(segmentRef1, segmentRef2), Arrays.asList(segmentRef2, segmentRef3)));

    for (Memberships memberships : membershipsList) {
      try (BigSegmentStore store = makeEmptyStore()) {
        setSegments(prefix, fakeUserHash, memberships.inclusions, memberships.exclusions);
        Membership membership = store.getMembership(fakeUserHash);
        assertEqualMembership(createMembershipFromSegmentRefs(memberships.inclusions, memberships.exclusions), membership);
      }
    }
  }

  private static class Memberships {
    final Iterable<String> inclusions;
    final Iterable<String> exclusions;

    Memberships(Iterable<String> inclusions, Iterable<String> exclusions) {
      this.inclusions = inclusions == null ? Collections.emptyList() : inclusions;
      this.exclusions = exclusions == null ? Collections.emptyList() : exclusions;
    }
  }

  private void assertEqualMembership(Membership expected, Membership actual) {
    if (actual.getClass().getCanonicalName()
        .startsWith("com.launchdarkly.sdk.server.interfaces.BigSegmentStoreTypes.MembershipBuilder")) {
      // The store implementation is using our standard membership types, so we can rely on the
      // standard equality test for those
      assertEquals(expected, actual);
    } else {
      // The store implementation has implemented Membership some other way, so we have to check for
      // the inclusion or exclusion of specific keys
      for (String segmentRef : allSegmentRefs) {
        Boolean expectedMembership = expected.checkMembership(segmentRef);
        Boolean actualMembership = actual.checkMembership(segmentRef);
        if (!Objects.equals(actualMembership, expectedMembership)) {
          Assert.fail(String.format("expected membership for %s to be %s but was %s",
              segmentRef,
              expectedMembership == null ? "null" : expectedMembership.toString(),
              actualMembership == null ? "null" : actualMembership.toString()));
        }
      }
    }
  }

  /**
   * Test classes should override this method to return a configured factory for the subject
   * implementation of {@link BigSegmentStore}.
   * <p>
   * If the prefix string is {@code null} or the empty string, it should use the default prefix
   * defined by the data store implementation. The factory must include any necessary configuration
   * that may be appropriate for the test environment (for instance, pointing it to a database
   * instance that has been set up for the tests).
   *
   * @param prefix the database prefix
   * @return the configured factory
   */
  protected abstract ComponentConfigurer<BigSegmentStore> makeStore(String prefix);

  /**
   * Test classes should override this method to clear all data from the underlying data store for
   * the specified prefix string.
   *
   * @param prefix the database prefix
   */
  protected abstract void clearData(String prefix);

  /**
   * Test classes should override this method to update the store metadata for the given prefix in
   * the underlying data store.
   *
   * @param prefix   the database prefix
   * @param metadata the data to write to the store
   */
  protected abstract void setMetadata(String prefix, StoreMetadata metadata);

  /**
   * Test classes should override this method to update the store metadata for the given prefix in
   * the underlying data store.
   *
   * @param prefix              the database prefix
   * @param userHashKey         the hashed user key
   * @param includedSegmentRefs segment references to be included
   * @param excludedSegmentRefs segment references to be excluded
   */
  protected abstract void setSegments(String prefix,
                                      String userHashKey,
                                      Iterable<String> includedSegmentRefs,
                                      Iterable<String> excludedSegmentRefs);
}
