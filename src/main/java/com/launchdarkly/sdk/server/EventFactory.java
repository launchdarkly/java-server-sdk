package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.ErrorKind;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.Event.Custom;
import com.launchdarkly.sdk.server.interfaces.Event.FeatureRequest;
import com.launchdarkly.sdk.server.interfaces.Event.Identify;

import java.util.function.Supplier;

abstract class EventFactory {
  public static final EventFactory DEFAULT = new Default(false, null);
  public static final EventFactory DEFAULT_WITH_REASONS = new Default(true, null);
  
  abstract Event.FeatureRequest newFeatureRequestEvent(
      DataModel.FeatureFlag flag,
      LDUser user,
      LDValue value,
      int variationIndex,
      EvaluationReason reason,
      LDValue defaultValue,
      String prereqOf
      );

  abstract Event.FeatureRequest newUnknownFeatureRequestEvent(
      String key,
      LDUser user,
      LDValue defaultValue,
      EvaluationReason.ErrorKind errorKind
      );
  
  abstract Event.Custom newCustomEvent(String key, LDUser user, LDValue data, Double metricValue);
  
  abstract Event.Identify newIdentifyEvent(LDUser user);

  abstract Event.AliasEvent newAliasEvent(LDUser user, LDUser previousUser);

  final Event.FeatureRequest newFeatureRequestEvent(
      DataModel.FeatureFlag flag,
      LDUser user,
      Evaluator.EvalResult details,
      LDValue defaultValue
      ) {
    return newFeatureRequestEvent(
        flag,
        user,
        details == null ? null : details.getValue(),
        details == null ? -1 : details.getVariationIndex(),
        details == null ? null : details.getReason(),
        defaultValue,
        null
        );
  }

  final Event.FeatureRequest newDefaultFeatureRequestEvent(
      DataModel.FeatureFlag flag,
      LDUser user,
      LDValue defaultVal,
      EvaluationReason.ErrorKind errorKind
      ) {
    return newFeatureRequestEvent(
        flag,
        user,
        defaultVal,
        -1,
        EvaluationReason.error(errorKind),
        defaultVal,
        null
        );
  }
  
  final Event.FeatureRequest newPrerequisiteFeatureRequestEvent(
      DataModel.FeatureFlag prereqFlag,
      LDUser user,
      Evaluator.EvalResult details,
      DataModel.FeatureFlag prereqOf
      ) {
    return newFeatureRequestEvent(
        prereqFlag,
        user,
        details == null ? null : details.getValue(),
        details == null ? -1 : details.getVariationIndex(),
        details == null ? null : details.getReason(),
        LDValue.ofNull(),
        prereqOf.getKey()
    );
  }
  
  static final Event.FeatureRequest newDebugEvent(Event.FeatureRequest from) {
    return new Event.FeatureRequest(
        from.getCreationDate(),
        from.getKey(),
        from.getUser(),
        from.getVersion(),
        from.getVariation(),
        from.getValue(),
        from.getDefaultVal(),
        from.getReason(),
        from.getPrereqOf(),
        from.isTrackEvents(),
        from.getDebugEventsUntilDate(),
        true
        );
  }
  
  static class Default extends EventFactory {
    private final boolean includeReasons;
    private final Supplier<Long> timestampFn;
    
    Default(boolean includeReasons, Supplier<Long> timestampFn) {
      this.includeReasons = includeReasons;
      this.timestampFn = timestampFn != null ? timestampFn : (() -> System.currentTimeMillis());
    }
  
    @Override
    final Event.FeatureRequest newFeatureRequestEvent(DataModel.FeatureFlag flag, LDUser user, LDValue value,
        int variationIndex, EvaluationReason reason, LDValue defaultValue, String prereqOf){
      boolean requireExperimentData = isExperiment(flag, reason);
      return new Event.FeatureRequest(
          timestampFn.get(),
          flag.getKey(),
          user,
          flag.getVersion(),
          variationIndex,
          value,
          defaultValue,
          (requireExperimentData || includeReasons) ? reason : null,
          prereqOf,
          requireExperimentData || flag.isTrackEvents(),
          flag.getDebugEventsUntilDate() == null ? 0 : flag.getDebugEventsUntilDate().longValue(),
          false
      );
    }

    @Override
    final Event.FeatureRequest newUnknownFeatureRequestEvent(
        String key,
        LDUser user,
        LDValue defaultValue,
        EvaluationReason.ErrorKind errorKind
        ) {
      return new Event.FeatureRequest(
          timestampFn.get(),
          key,
          user,
          -1,
          -1,
          defaultValue,
          defaultValue,
          includeReasons ? EvaluationReason.error(errorKind) : null,
          null,
          false,
          0,
          false
      ); 
    }
    
    @Override
    Event.Custom newCustomEvent(String key, LDUser user, LDValue data, Double metricValue) {
      return new Event.Custom(timestampFn.get(), key, user, data, metricValue);
    }
    
    @Override
    Event.Identify newIdentifyEvent(LDUser user) {
      return new Event.Identify(timestampFn.get(), user);
    }

    @Override
    Event.AliasEvent newAliasEvent(LDUser user, LDUser previousUser) {
      return new Event.AliasEvent(timestampFn.get(), user, previousUser);
    }
  }

  static final class Disabled extends EventFactory {
    static final Disabled INSTANCE = new Disabled();

    @Override
    final FeatureRequest newFeatureRequestEvent(FeatureFlag flag, LDUser user, LDValue value, int variationIndex,
        EvaluationReason reason, LDValue defaultValue, String prereqOf) {
      return null;
    }

    @Override
    final FeatureRequest newUnknownFeatureRequestEvent(String key, LDUser user, LDValue defaultValue, ErrorKind errorKind) {
      return null;
    }

    @Override
    final Custom newCustomEvent(String key, LDUser user, LDValue data, Double metricValue) {
      return null;
    }

    @Override
    final Identify newIdentifyEvent(LDUser user) {
      return null;
    }

    @Override
    Event.AliasEvent newAliasEvent(LDUser user, LDUser previousUser) {
      return null;
    }
  }
  
  private static boolean isExperiment(DataModel.FeatureFlag flag, EvaluationReason reason) {
    if (reason == null) {
      // doesn't happen in real life, but possible in testing
      return false;
    }
    switch (reason.getKind()) { 
    case FALLTHROUGH:
      return flag.isTrackEventsFallthrough();
    case RULE_MATCH:
      int ruleIndex = reason.getRuleIndex();
      // Note, it is OK to rely on the rule index rather than the unique ID in this context, because the
      // FeatureFlag that is passed to us here *is* necessarily the same version of the flag that was just
      // evaluated, so we cannot be out of sync with its rule list.
      if (ruleIndex >= 0 && ruleIndex < flag.getRules().size()) {
        DataModel.Rule rule = flag.getRules().get(ruleIndex);
        return rule.isTrackEvents();
      }
      return false;
    default:
      return false;
    }
  }
}
