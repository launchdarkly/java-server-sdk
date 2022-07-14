package com.launchdarkly.sdk.server.interfaces;

import static com.launchdarkly.sdk.server.TestUtil.assertFullyEqual;
import static com.launchdarkly.sdk.server.TestUtil.assertFullyUnequal;
import static com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.createMembershipFromSegmentRefs;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.Membership;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

@SuppressWarnings("javadoc")
public class BigSegmentMembershipBuilderTest {

    // MembershipBuilder is private to BigSegmentStoreTypes, we test it through
    // createMembershipFromSegmentRefs

    @Test
    public void empty() {
        Membership m0 = createMembershipFromSegmentRefs(null, null);
        Membership m1 = createMembershipFromSegmentRefs(Collections.emptyList(), null);
        Membership m2 = createMembershipFromSegmentRefs(null, Collections.emptyList());

        assertSame(m0, m1);
        assertSame(m0, m2);
        assertFullyEqual(m0, m1);

        assertNull(m0.checkMembership("arbitrary"));
    }

    @Test
    public void singleInclusion() {
        Membership m0 = createMembershipFromSegmentRefs(Collections.singleton("key1"), null);
        Membership m1 = createMembershipFromSegmentRefs(Collections.singleton("key1"), null);

        assertNotSame(m0, m1);
        assertFullyEqual(m0, m1);

        assertTrue(m0.checkMembership("key1"));
        assertNull(m0.checkMembership("key2"));

        assertFullyUnequal(m0, createMembershipFromSegmentRefs(null, Collections.singleton("key1")));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(Collections.singleton("key2"), null));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(null, null));
    }

    @Test
    public void multipleInclusions() {
        Membership m0 = createMembershipFromSegmentRefs(Arrays.asList("key1", "key2"), null);
        Membership m1 = createMembershipFromSegmentRefs(Arrays.asList("key2", "key1"), null);

        assertNotSame(m0, m1);
        assertFullyEqual(m0, m1);

        assertTrue(m0.checkMembership("key1"));
        assertTrue(m0.checkMembership("key2"));
        assertNull(m0.checkMembership("key3"));

        assertFullyUnequal(m0, createMembershipFromSegmentRefs(Arrays.asList("key1", "key2"), Collections.singleton("key3")));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(Arrays.asList("key1", "key3"), null));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(Collections.singleton("key1"), null));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(null, null));
    }

    @Test
    public void singleExclusion() {
        Membership m0 = createMembershipFromSegmentRefs(null, Collections.singleton("key1"));
        Membership m1 = createMembershipFromSegmentRefs(null, Collections.singleton("key1"));

        assertNotSame(m0, m1);
        assertFullyEqual(m0, m1);

        assertFalse(m0.checkMembership("key1"));
        assertNull(m0.checkMembership("key2"));

        assertFullyUnequal(m0, createMembershipFromSegmentRefs(Collections.singleton("key1"), null));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(null, Collections.singleton("key2")));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(null, null));
    }

    @Test
    public void multipleExclusions() {
        Membership m0 = createMembershipFromSegmentRefs(null, Arrays.asList("key1", "key2"));
        Membership m1 = createMembershipFromSegmentRefs(null, Arrays.asList("key2", "key1"));

        assertNotSame(m0, m1);
        assertFullyEqual(m0, m1);

        assertFalse(m0.checkMembership("key1"));
        assertFalse(m0.checkMembership("key2"));
        assertNull(m0.checkMembership("key3"));

        assertFullyUnequal(m0, createMembershipFromSegmentRefs(Collections.singleton("key3"), Arrays.asList("key1", "key2")));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(null, Arrays.asList("key1", "key3")));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(null, Collections.singleton("key1")));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(null, null));
    }

    @Test
    public void inclusionsAndExclusions() {
        // key1 is included; key2 is included and excluded, therefore it's included; key3 is excluded
        Membership m0 = createMembershipFromSegmentRefs(Arrays.asList("key1", "key2"), Arrays.asList("key2", "key3"));
        Membership m1 = createMembershipFromSegmentRefs(Arrays.asList("key2", "key1"), Arrays.asList("key3", "key2"));

        assertNotSame(m0, m1);
        assertFullyEqual(m0, m1);

        assertTrue(m0.checkMembership("key1"));
        assertTrue(m0.checkMembership("key2"));
        assertFalse(m0.checkMembership("key3"));
        assertNull(m0.checkMembership("key4"));

        assertFullyUnequal(m0, createMembershipFromSegmentRefs(Arrays.asList("key1", "key2"), Arrays.asList("key2", "key3", "key4")));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(Arrays.asList("key1", "key2", "key3"), Arrays.asList("key2", "key3")));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(Collections.singleton("key1"), null));
        assertFullyUnequal(m0, createMembershipFromSegmentRefs(null, null));
    }
}
