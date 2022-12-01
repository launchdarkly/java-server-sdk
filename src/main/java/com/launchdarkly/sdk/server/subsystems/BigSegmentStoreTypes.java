package com.launchdarkly.sdk.server.subsystems;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Types that are used by the {@link BigSegmentStore} interface.
 *
 * @since 5.7.0
 */
public abstract class BigSegmentStoreTypes {
  private BigSegmentStoreTypes() {
  }

  /**
   * A query interface returned by {@link BigSegmentStore#getMembership(String)}.
   * <p>
   * It is associated with a single user, and provides the ability to check whether that user is
   * included in or excluded from any number of Big Segments.
   * <p>
   * This is an immutable snapshot of the state for this user at the time
   * {@link BigSegmentStore#getMembership(String)} was called. Calling
   * {@link #checkMembership(String)} should not cause the state to be queried again.
   * Implementations should be safe for concurrent access by multiple threads.
   */
  public static interface Membership {
    /**
     * Tests whether the user is explicitly included or explicitly excluded in the specified
     * segment, or neither.
     * <p>
     * The segment is identified by a {@code segmentRef} which is not the same as the segment key:
     * it includes the key but also versioning information that the SDK will provide. The store
     * implementation should not be concerned with the format of this.
     * <p>
     * If the user is explicitly included (regardless of whether the user is also explicitly
     * excluded or not-- that is, inclusion takes priority over exclusion), the method returns a
     * {@code true} value.
     * <p>
     * If the user is explicitly excluded, and is not explicitly included, the method returns a
     * {@code false} value.
     * <p>
     * If the user's status in the segment is undefined, the method returns {@code null}.
     *
     * @param segmentRef a string representing the segment query
     * @return boolean for explicit inclusion/exclusion, null for unspecified
     */
    Boolean checkMembership(String segmentRef);
  }

  /**
   * Convenience method for creating an implementation of {@link Membership}.
   * <p>
   * This method is intended to be used by Big Segment store implementations; application code does
   * not need to use it.
   * <p>
   * Store implementations are free to implement {@link Membership} in any way that they find
   * convenient and efficient, depending on what format they obtain values in from the database, but
   * this method provides a simple way to do it as long as there are iterables of included and
   * excluded segment references. As described in {@link Membership}, a {@code segmentRef} is not
   * the same as the key property in the segment data model; it includes the key but also versioning
   * information that the SDK will provide. The store implementation should not be concerned with
   * the format of this.
   * <p>
   * The returned object's {@link Membership#checkMembership(String)} method will return
   * {@code true} for any {@code segmentRef} that is in the included list,
   * {@code false} for any {@code segmentRef} that is in the excluded list and not also in the
   * included list (that is, inclusions override exclusions), and {@code null} for all others.
   * <p>
   * This method is optimized to return a singleton empty membership object whenever the inclusion
   * and exclusions lists are both empty.
   * <p>
   * The returned object implements {@link Object#equals(Object)} in such a way that it correctly
   * tests equality when compared to any object returned from this factory method, but it is always
   * unequal to any other types of objects.
   *
   * @param includedSegmentRefs the inclusion list (null is equivalent to an empty iterable)
   * @param excludedSegmentRefs the exclusion list (null is equivalent to an empty iterable)
   * @return an {@link Membership}
   */
  public static Membership createMembershipFromSegmentRefs(
      Iterable<String> includedSegmentRefs,
      Iterable<String> excludedSegmentRefs) {
    MembershipBuilder membershipBuilder = new MembershipBuilder();
    // we must add excludes first so includes will override them
    membershipBuilder.addRefs(excludedSegmentRefs, false);
    membershipBuilder.addRefs(includedSegmentRefs, true);
    return membershipBuilder.build();
  }

  /**
   * Values returned by {@link BigSegmentStore#getMetadata()}.
   */
  public static final class StoreMetadata {
    private final long lastUpToDate;

    /**
     * Constructor for a {@link StoreMetadata}.
     *
     * @param lastUpToDate the Unix millisecond timestamp of the last update
     */
    public StoreMetadata(long lastUpToDate) {
      this.lastUpToDate = lastUpToDate;
    }

    /**
     * The timestamp of the last update to the {@link BigSegmentStore}.
     *
     * @return the last update timestamp as Unix milliseconds
     */
    public long getLastUpToDate() {
      return this.lastUpToDate;
    }
  }

  private static class MembershipBuilder {
    private boolean nonEmpty;
    private String firstValue;
    private boolean firstValueIncluded;
    private HashMap<String, Boolean> map;

    void addRefs(Iterable<String> segmentRefs, boolean included) {
      if (segmentRefs == null) {
        return;
      }
      for (String s : segmentRefs) {
        if (s == null) {
          continue;
        }
        if (nonEmpty) {
          if (map == null) {
            map = new HashMap<>();
            map.put(firstValue, firstValueIncluded);
          }
          map.put(s, included);
        } else {
          firstValue = s;
          firstValueIncluded = included;
          nonEmpty = true;
        }
      }
    }

    Membership build() {
      if (nonEmpty) {
        if (map != null) {
          return new MapMembership(map);
        }
        return new SingleValueMembership(firstValue, firstValueIncluded);
      }
      return EmptyMembership.instance;
    }

    private static final class EmptyMembership implements Membership {
      static final EmptyMembership instance = new EmptyMembership();

      @Override
      public Boolean checkMembership(String segmentRef) {
        return null;
      }

      @Override
      public boolean equals(Object o) {
        return o instanceof EmptyMembership;
      }

      @Override
      public int hashCode() {
        return 0;
      }
    }

    private static final class SingleValueMembership implements Membership {
      private final String segmentRef;
      private final boolean included;

      SingleValueMembership(String segmentRef, boolean included) {
        this.segmentRef = segmentRef;
        this.included = included;
      }

      @Override
      public Boolean checkMembership(String segmentRef) {
        return this.segmentRef.equals(segmentRef) ? included : null;
      }

      @Override
      public boolean equals(Object o) {
        if (!(o instanceof SingleValueMembership)) {
          return false;
        }
        SingleValueMembership other = (SingleValueMembership) o;
        return segmentRef.equals(other.segmentRef) && included == other.included;
      }

      @Override
      public int hashCode() {
        return segmentRef.hashCode() + (included ? 1 : 0);
      }
    }

    private static final class MapMembership implements Membership {
      private final Map<String, Boolean> map;

      private MapMembership(Map<String, Boolean> map) {
        this.map = map;
      }

      @Override
      public Boolean checkMembership(String segmentRef) {
        return map.get(segmentRef);
      }

      @Override
      public boolean equals(Object o) {
        return o instanceof MapMembership && map.equals(((MapMembership) o).map);
      }

      @Override
      public int hashCode() {
        return Objects.hash(map);
      }
    }
  }
}
