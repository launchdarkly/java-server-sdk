package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.ErrorKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.subsystems.Event;
import com.launchdarkly.sdk.server.subsystems.Event.Custom;
import com.launchdarkly.sdk.server.subsystems.Event.FeatureRequest;
import com.launchdarkly.sdk.server.subsystems.Event.Identify;

import java.util.function.Supplier;

abstract class EventFactory {
  public static final EventFactory DEFAULT = new Default(false, null);
  public static final EventFactory DEFAULT_WITH_REASONS = new Default(true, null);
  
  abstract Event.FeatureRequest newFeatureRequestEvent(
      FeatureFlag flag,
      LDContext context,
      LDValue value,
      int variationIndex,
      EvaluationReason reason,
      boolean forceReasonTracking,
      LDValue defaultValue,
      String prereqOf
      );

  abstract Event.FeatureRequest newUnknownFeatureRequestEvent(
      String key,
      LDContext context,
      LDValue defaultValue,
      EvaluationReason.ErrorKind errorKind
      );
  
  abstract Event.Custom newCustomEvent(String key, LDContext context, LDValue data, Double metricValue);
  
  abstract Event.Identify newIdentifyEvent(LDContext context);

  final Event.FeatureRequest newFeatureRequestEvent(
      FeatureFlag flag,
      LDContext context,
      EvalResult result,
      LDValue defaultValue
      ) {
    return newFeatureRequestEvent(
        flag,
        context,
        result == null ? null : result.getValue(),
        result == null ? -1 : result.getVariationIndex(),
        result == null ? null : result.getReason(),
        result != null && result.isForceReasonTracking(),
        defaultValue,
        null
        );
  }

  final Event.FeatureRequest newDefaultFeatureRequestEvent(
      FeatureFlag flag,
      LDContext context,
      LDValue defaultVal,
      EvaluationReason.ErrorKind errorKind
      ) {
    return newFeatureRequestEvent(
        flag,
        context,
        defaultVal,
        -1,
        EvaluationReason.error(errorKind),
        false,
        defaultVal,
        null
        );
  }
  
  final Event.FeatureRequest newPrerequisiteFeatureRequestEvent(
      DataModel.FeatureFlag prereqFlag,
      LDContext context,
      EvalResult result,
      DataModel.FeatureFlag prereqOf
      ) {
    return newFeatureRequestEvent(
        prereqFlag,
        context,
        result == null ? null : result.getValue(),
        result == null ? -1 : result.getVariationIndex(),
        result == null ? null : result.getReason(),
        result != null && result.isForceReasonTracking(),
        LDValue.ofNull(),
        prereqOf.getKey()
    );
  }
  
  static final Event.FeatureRequest newDebugEvent(Event.FeatureRequest from) {
    return new Event.FeatureRequest(
        from.getCreationDate(),
        from.getKey(),
        from.getContext(),
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
    final Event.FeatureRequest newFeatureRequestEvent(FeatureFlag flag, LDContext context,
        LDValue value, int variationIndex, EvaluationReason reason, boolean forceReasonTracking,
        LDValue defaultValue, String prereqOf){
      return new Event.FeatureRequest(
          timestampFn.get(),
          flag.getKey(),
          context,
          flag.getVersion(),
          variationIndex,
          value,
          defaultValue,
          (forceReasonTracking || includeReasons) ? reason : null,
          prereqOf,
          forceReasonTracking || flag.isTrackEvents(),
          flag.getDebugEventsUntilDate() == null ? 0 : flag.getDebugEventsUntilDate().longValue(),
          false
      );
    }

    @Override
    final Event.FeatureRequest newUnknownFeatureRequestEvent(
        String key,
        LDContext context,
        LDValue defaultValue,
        EvaluationReason.ErrorKind errorKind
        ) {
      return new Event.FeatureRequest(
          timestampFn.get(),
          key,
          context,
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
    Event.Custom newCustomEvent(String key, LDContext context, LDValue data, Double metricValue) {
      return new Event.Custom(timestampFn.get(), key, context, data, metricValue);
    }
    
    @Override
    Event.Identify newIdentifyEvent(LDContext context) {
      return new Event.Identify(timestampFn.get(), context);
    }
  }

  static final class Disabled extends EventFactory {
    static final Disabled INSTANCE = new Disabled();

    @Override
    final FeatureRequest newFeatureRequestEvent(FeatureFlag flag, LDContext context, LDValue value, int variationIndex,
        EvaluationReason reason, boolean inExperiment, LDValue defaultValue, String prereqOf) {
      return null;
    }

    @Override
    final FeatureRequest newUnknownFeatureRequestEvent(String key, LDContext context, LDValue defaultValue, ErrorKind errorKind) {
      return null;
    }

    @Override
    final Custom newCustomEvent(String key, LDContext context, LDValue data, Double metricValue) {
      return null;
    }

    @Override
    final Identify newIdentifyEvent(LDContext context) {
      return null;
    }
  }
}
