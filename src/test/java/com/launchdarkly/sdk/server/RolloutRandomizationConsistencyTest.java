package com.launchdarkly.sdk.server;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel.Rollout;
import com.launchdarkly.sdk.server.DataModel.RolloutKind;
import com.launchdarkly.sdk.server.DataModel.VariationOrRollout;
import com.launchdarkly.sdk.server.DataModel.WeightedVariation;

import org.junit.Test;

/*
 * Note: These tests are meant to be exact duplicates of tests
 * in other SDKs. Do not change any of the values unless they 
 * are also changed in other SDKs. These are not traditional behavioral 
 * tests so much as consistency tests to guarantee that the implementation 
 * is identical across SDKs.
 */
public class RolloutRandomizationConsistencyTest {
    private Integer noSeed = null;

    private static Rollout buildRollout(boolean isExperiment, boolean untrackedVariations) {
        List<WeightedVariation> variations = new ArrayList<>();
        variations.add(new WeightedVariation(0, 10000, untrackedVariations));
        variations.add(new WeightedVariation(1, 20000, untrackedVariations));
        variations.add(new WeightedVariation(0, 70000, true));
        UserAttribute bucketBy = UserAttribute.KEY;
        RolloutKind kind = isExperiment ? RolloutKind.experiment : RolloutKind.rollout;
        Integer seed = 61;
        Rollout rollout = new Rollout(variations, bucketBy, kind, seed);
        return rollout;
    }
    
    @Test
    public void variationIndexForUserInExperimentTest() {
        // seed here carefully chosen so users fall into different buckets
        Rollout rollout = buildRollout(true, false);
        VariationOrRollout vr = new VariationOrRollout(null, rollout);

        LDUser user1 = new LDUser("userKeyA");
        EvaluatedVariation ev1 = EvaluatorBucketing.variationIndexForUser(vr, user1, "hashKey", "saltyA");
        // bucketVal = 0.09801207
        assertEquals(new Integer(0), ev1.getIndex());
        assert(ev1.isInExperiment());

        LDUser user2 = new LDUser("userKeyB");
        EvaluatedVariation ev2 = EvaluatorBucketing.variationIndexForUser(vr, user2, "hashKey", "saltyA");
        // bucketVal = 0.14483777
        assertEquals(new Integer(1), ev2.getIndex());
        assert(ev2.isInExperiment());

        LDUser user3 = new LDUser("userKeyC");
        EvaluatedVariation ev3 = EvaluatorBucketing.variationIndexForUser(vr, user3, "hashKey", "saltyA");
        // bucketVal = 0.9242641
        assertEquals(new Integer(0), ev3.getIndex());
        assert(!ev3.isInExperiment());
    }

    @Test
    public void bucketUserByKeyTest() {
        LDUser user1 = new LDUser("userKeyA");
        Float point1 = EvaluatorBucketing.bucketUser(noSeed, user1, "hashKey", UserAttribute.KEY, "saltyA");
        assertEquals(0.42157587, point1, 0.0000001);

        LDUser user2 = new LDUser("userKeyB");
        Float point2 = EvaluatorBucketing.bucketUser(noSeed, user2, "hashKey", UserAttribute.KEY, "saltyA");
        assertEquals(0.6708485, point2, 0.0000001);

        LDUser user3 = new LDUser("userKeyC");
        Float point3 = EvaluatorBucketing.bucketUser(noSeed, user3, "hashKey", UserAttribute.KEY, "saltyA");
        assertEquals(0.10343106, point3, 0.0000001);
    }

    @Test
    public void bucketUserWithSeedTest() {
        Integer seed = 61;

        LDUser user1 = new LDUser("userKeyA");
        Float point1 = EvaluatorBucketing.bucketUser(seed, user1, "hashKey", UserAttribute.KEY, "saltyA");
        assertEquals(0.09801207, point1, 0.0000001);

        LDUser user2 = new LDUser("userKeyB");
        Float point2 = EvaluatorBucketing.bucketUser(seed, user2, "hashKey", UserAttribute.KEY, "saltyA");
        assertEquals(0.14483777, point2, 0.0000001);

        LDUser user3 = new LDUser("userKeyC");
        Float point3 = EvaluatorBucketing.bucketUser(seed, user3, "hashKey", UserAttribute.KEY, "saltyA");
        assertEquals(0.9242641, point3, 0.0000001);
    }

}
