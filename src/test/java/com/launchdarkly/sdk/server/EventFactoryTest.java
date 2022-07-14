package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.subsystems.Event.FeatureRequest;

import org.junit.Test;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class EventFactoryTest {
    private static final LDContext BASE_USER = LDContext.create("x");
    private static final LDValue SOME_VALUE = LDValue.of("value");
    private static final int SOME_VARIATION = 11;
    private static final EvaluationReason SOME_REASON = EvaluationReason.fallthrough();
    private static final EvalResult SOME_RESULT = EvalResult.of(SOME_VALUE, SOME_VARIATION, SOME_REASON); 
    private static final LDValue DEFAULT_VALUE = LDValue.of("default");
    
    @Test
    public void flagKeyIsSetInFeatureEvent() {
      FeatureFlag flag = flagBuilder("flagkey").build();
      FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, BASE_USER, SOME_RESULT, DEFAULT_VALUE);

      assertEquals(flag.getKey(), fr.getKey());
    }

    @Test
    public void flagVersionIsSetInFeatureEvent() {
      FeatureFlag flag = flagBuilder("flagkey").build();
      FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, BASE_USER, SOME_RESULT, DEFAULT_VALUE);

      assertEquals(flag.getVersion(), fr.getVersion());
    }
    
    @Test
    public void contextIsSetInFeatureEvent() {
      FeatureFlag flag = flagBuilder("flagkey").build();
      FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, BASE_USER, SOME_RESULT, DEFAULT_VALUE);

      assertEquals(BASE_USER, fr.getContext());
    }
    
    @Test
    public void valueIsSetInFeatureEvent() {
      FeatureFlag flag = flagBuilder("flagkey").build();
      FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, BASE_USER, SOME_RESULT, DEFAULT_VALUE);

      assertEquals(SOME_VALUE, fr.getValue());
    }
    
    @Test
    public void variationIsSetInFeatureEvent() {
      FeatureFlag flag = flagBuilder("flagkey").build();
      FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, BASE_USER, SOME_RESULT, DEFAULT_VALUE);

      assertEquals(SOME_VARIATION, fr.getVariation());
    }
    
    @Test
    public void reasonIsNormallyNotIncludedWithDefaultEventFactory() {
      FeatureFlag flag = flagBuilder("flagkey").build();
      FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, BASE_USER, SOME_RESULT, DEFAULT_VALUE);

      assertNull(fr.getReason());
    }

    @Test
    public void reasonIsIncludedWithEventFactoryThatIsConfiguredToIncludedReasons() {
      FeatureFlag flag = flagBuilder("flagkey").build();
      FeatureRequest fr = EventFactory.DEFAULT_WITH_REASONS.newFeatureRequestEvent(
          flag, BASE_USER, SOME_RESULT, DEFAULT_VALUE);

      assertEquals(SOME_REASON, fr.getReason());
    }

    @Test
    public void reasonIsIncludedIfForceReasonTrackingIsTrue() {
        FeatureFlag flag = flagBuilder("flagkey").build();
        FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, BASE_USER,
            SOME_RESULT.withForceReasonTracking(true), DEFAULT_VALUE);

        assertEquals(SOME_REASON, fr.getReason());
    }
    @Test
    public void trackEventsIsNormallyFalse() {
        FeatureFlag flag = flagBuilder("flagkey").build();
        FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, BASE_USER, SOME_RESULT, DEFAULT_VALUE);

        assert(!fr.isTrackEvents());
    }

    @Test
    public void trackEventsIsTrueIfItIsTrueInFlag() {
        FeatureFlag flag = flagBuilder("flagkey")
            .trackEvents(true)
            .build();
        FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, BASE_USER, SOME_RESULT, DEFAULT_VALUE);

        assert(fr.isTrackEvents());
    }

    @Test
    public void trackEventsIsTrueIfForceReasonTrackingIsTrue() {
        FeatureFlag flag = flagBuilder("flagkey").build();
        FeatureRequest fr = EventFactory.DEFAULT.newFeatureRequestEvent(flag, BASE_USER,
            SOME_RESULT.withForceReasonTracking(true), DEFAULT_VALUE);

        assert(fr.isTrackEvents());
    }

}
