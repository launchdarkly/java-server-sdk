package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.DataModel.Rollout;
import com.launchdarkly.sdk.server.DataModel.RolloutKind;
import com.launchdarkly.sdk.server.DataModel.VariationOrRollout;
import com.launchdarkly.sdk.server.DataModel.WeightedVariation;
import com.launchdarkly.sdk.server.subsystems.Event.FeatureRequest;

import org.junit.Test;

import static com.launchdarkly.sdk.server.ModelBuilders.*;

import java.util.ArrayList;
import java.util.List;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;

public class EventFactoryTest {
    private static final LDUser BASE_USER = new LDUser.Builder("x").build();
    private static Rollout buildRollout(boolean isExperiment, boolean untrackedVariations) {
        List<WeightedVariation> variations = new ArrayList<>();
        variations.add(new WeightedVariation(1, 50000, untrackedVariations));
        variations.add(new WeightedVariation(2, 50000, untrackedVariations));
        UserAttribute bucketBy = UserAttribute.KEY;
        RolloutKind kind = isExperiment ? RolloutKind.experiment : RolloutKind.rollout;
        Integer seed = 123;
        Rollout rollout = new Rollout(variations, bucketBy, kind, seed);
        return rollout;
    }

    @Test
    public void trackEventFalseTest() {
        DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(false).build();
        LDUser user = new LDUser("moniker");
        FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user, null, null);

        assert(!fr.isTrackEvents());
    }

    @Test
    public void trackEventTrueTest() {
        DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
        LDUser user = new LDUser("moniker");
        FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user, null, null);

        assert(fr.isTrackEvents());
    }

    @Test
    public void trackEventTrueWhenTrackEventsFalseButExperimentFallthroughReasonTest() {
        Rollout rollout = buildRollout(true, false);
        VariationOrRollout vr = new VariationOrRollout(null, rollout);

        DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(false)
            .fallthrough(vr).build();
        LDUser user = new LDUser("moniker");
        FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user, null, 0, 
            EvaluationReason.fallthrough(true), null, null);

        assert(fr.isTrackEvents());
    }

    @Test
    public void trackEventTrueWhenTrackEventsFalseButExperimentRuleMatchReasonTest() {
        Rollout rollout = buildRollout(true, false);
        
        DataModel.Clause clause = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of(BASE_USER.getKey()));
        DataModel.Rule rule = ruleBuilder().id("ruleid0").clauses(clause).rollout(rollout).build();
    
        DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(false)
            .rules(rule).build();
        LDUser user = new LDUser("moniker");
        FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user, null, 0, 
            EvaluationReason.ruleMatch(0, "something", true), null, null);

        assert(fr.isTrackEvents());
    }

}
